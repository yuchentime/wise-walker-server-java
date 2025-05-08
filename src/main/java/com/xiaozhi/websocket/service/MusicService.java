package com.xiaozhi.websocket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.utils.OpusProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 音乐服务，负责处理音乐播放和歌词同步
 */
@Service
public class MusicService {
    private static final Logger logger = LoggerFactory.getLogger(MusicService.class);

    private static final long OPUS_FRAME_INTERVAL_MS = AudioUtils.OPUS_FRAME_DURATION_MS;
    private static final String API_BASE_URL = "http://www.jsrc.top:5566";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OpusProcessor opusProcessor;

    @Autowired
    private SessionManager sessionManager;
    
    @Autowired
    private AudioService audioService;

    // 存储每个会话的当前歌词信息
    private final Map<String, List<LyricLine>> sessionLyrics = new ConcurrentHashMap<>();

    // 存储每个会话的当前播放时间
    private final Map<String, AtomicLong> playTime = new ConcurrentHashMap<>();

    // 存储每个会话的音频文件路径，用于播放完成后删除
    private final Map<String, String> sessionAudioFiles = new ConcurrentHashMap<>();

    /**
     * 歌词行数据结构
     */
    private static class LyricLine {
        long timeMs;  // 歌词时间点（毫秒）
        String text;  // 歌词文本

        public LyricLine(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    /**
     * 搜索并播放音乐
     * 
     * @param session WebSocketSession会话
     * @param song 歌曲名
     * @param artist 艺术家名（可选）
     * @return 操作完成的Mono
     */
    public Mono<Void> playMusic(WebSocketSession session, String song, String artist) {
        String sessionId = session.getId();

        // 重置播放时间
        playTime.computeIfAbsent(sessionId, k -> new AtomicLong()).set(0);

        // 清理之前的音频文件（如果有）
        cleanupAudioFile(sessionId);
        
        return Mono.fromCallable(() -> {
            // 1. 获取音乐信息
            Map<String, String> musicInfo = getMusicInfo(song, artist);
            if (musicInfo == null) {
                throw new RuntimeException("无法找到歌曲: " + song + (artist != null ? " - " + artist : ""));
            }

            // 2. 下载音频文件到本地临时目录，使用随机文件名避免冲突
            String audioUrl = musicInfo.get("audioUrl");
            String randomName = "music_" + sessionId + "_" + UUID.randomUUID().toString() + ".mp3";
            String audioPath = downloadFile(audioUrl, randomName);

            // 保存音频文件路径，用于播放完成后删除
            sessionAudioFiles.put(sessionId, audioPath);

            // 3. 解析歌词
            String lyricUrl = musicInfo.get("lyricUrl");
            // 直接在线获取歌词，无需保存到本地
            List<LyricLine> lyrics = parseLyrics(lyricUrl);
            sessionLyrics.put(sessionId, lyrics);

            return audioPath;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(audioPath -> {
            // 发送音乐开始消息
            return audioService.sendStart(session)
                    .then(sendAudioWithLyrics(session, audioPath));
        })
        .onErrorResume(error -> {
            logger.error("播放音乐时发生错误 - SessionId: {}, Song: {}, Artist: {}", 
                    sessionId, song, artist, error);
            return audioService.sendStop(session);
        })
        .doFinally(signalType -> {
            // 播放完成后删除音频文件
            cleanupAudioFile(sessionId);
        });
    }

    /**
     * 清理之前的音频文件
     */
    private void cleanupAudioFile(String sessionId) {
        String audioPath = sessionAudioFiles.remove(sessionId);
        if (audioPath != null) {
            try {
                Files.deleteIfExists(Paths.get(audioPath));
            } catch (Exception e) {
                logger.warn("删除音频文件失败: {}", audioPath, e);
            }
        }
        // 清理会话的歌词数据
        sessionLyrics.remove(sessionId);
    }

    /**
     * 发送音频和同步歌词
     */
    private Mono<Void> sendAudioWithLyrics(WebSocketSession session, String audioPath) {
        String sessionId = session.getId();

        return Mono.fromCallable(() -> {
            File audioFile = new File(audioPath);
            if (!audioFile.exists()) {
                logger.warn("音频文件不存在: {}", audioPath);
                return null;
            }

            byte[] audioData = AudioUtils.readAsPcm(audioPath);

            return opusProcessor.pcmToOpus(
                session.getId(), audioData);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(frames -> {
            List<LyricLine> lyrics = sessionLyrics.getOrDefault(sessionId, Collections.emptyList());
            AtomicLong currPlayTime = playTime.computeIfAbsent(sessionId, k -> new AtomicLong(0));

            // 预处理歌词时间点，将毫秒时间转换为帧索引
            Map<Integer, String> lyricFrameMap = new HashMap<>();
            for (LyricLine line : lyrics) {
                // 计算歌词对应的帧索引
                int frameIndex = (int) (line.timeMs / OPUS_FRAME_INTERVAL_MS);
                if (frameIndex < frames.size()) {
                    lyricFrameMap.put(frameIndex, line.text);
                }
            }

            return Flux.range(0, frames.size())
                    // 使用固定间隔发送帧
                    .delayElements(Duration.ofMillis(OPUS_FRAME_INTERVAL_MS))
                    // 确保在boundedElastic调度器上执行，以避免阻塞
                    .publishOn(Schedulers.boundedElastic())
                    // 发送每一帧并处理歌词
                    .flatMap(i -> {
                        // 更新活跃时间
                        sessionManager.updateLastActivity(sessionId);
                        
                        // 更新当前播放时间
                        currPlayTime.set(i * OPUS_FRAME_INTERVAL_MS);
                        
                        // 先检查是否有对应这一帧的歌词需要发送
                        Mono<Void> lyricMono = Mono.empty();
                        String lyricText = lyricFrameMap.get(i);
                        if (lyricText != null) {
                            lyricMono = audioService.sendSentenceStart(session, lyricText);
                        }
                        // 先发送歌词，再发送音频帧
                        byte[] frame = frames.get(i);
                        return lyricMono.then(audioService.sendOpusFrame(session, frame));
                    })
                    .then(audioService.sendStop(session));
        })
        .onErrorResume(error -> {
            logger.error("处理音频时发生错误 - SessionId: {}", sessionId, error);
            return audioService.sendStop(session);
        });
    }

    /**
     * 获取音乐信息（音频URL和歌词URL）
     */
    private Map<String, String> getMusicInfo(String song, String artist) {
        try {
            StringBuilder urlBuilder = new StringBuilder(API_BASE_URL + "/stream_pcm?song=" + 
                    URLEncoder.encode(song, "UTF-8"));

            if (artist != null && !artist.isEmpty()) {
                urlBuilder.append("&artist=").append(URLEncoder.encode(artist, "UTF-8"));
            }
            
            URL url = new URL(urlBuilder.toString());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                logger.error("获取音乐信息失败，响应码: {}", responseCode);
                return null;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            // 解析JSON响应
            Map<String, Object> responseMap = objectMapper.readValue(response.toString(), Map.class);
            
            Map<String, String> result = new HashMap<>();
            
            // 检查API响应格式，支持两种可能的字段名
            String audioPath = (String) responseMap.get("audioPath");
            String audioUrl = (String) responseMap.get("audio_url");
            
            String lyricPath = (String) responseMap.get("lyricPath");
            String lyricUrl = (String) responseMap.get("lyric_url");
            
            // 优先使用直接URL，否则构建URL
            if (audioUrl != null && !audioUrl.isEmpty()) {
                if (!audioUrl.startsWith("http")) {
                    audioUrl = API_BASE_URL + audioUrl;
                }
                result.put("audioUrl", audioUrl);
            } else if (audioPath != null && !audioPath.isEmpty()) {
                result.put("audioUrl", API_BASE_URL + "/get_file?path=" + 
                        URLEncoder.encode(audioPath, "UTF-8") + 
                        "&name=" + URLEncoder.encode(song + ".mp3", "UTF-8"));
            } else {
                logger.error("API响应中缺少音频URL信息");
                return null;
            }
            
            if (lyricUrl != null && !lyricUrl.isEmpty()) {
                if (!lyricUrl.startsWith("http")) {
                    lyricUrl = API_BASE_URL + lyricUrl;
                }
                result.put("lyricUrl", lyricUrl);
            } else if (lyricPath != null && !lyricPath.isEmpty()) {
                result.put("lyricUrl", API_BASE_URL + "/get_file?path=" + 
                        URLEncoder.encode(lyricPath, "UTF-8") + 
                        "&name=" + URLEncoder.encode(song + ".lrc", "UTF-8"));
            } else {
                // 歌词可选，没有歌词也可以播放
                logger.warn("API响应中缺少歌词URL信息");
            }
            
            return result;
        } catch (Exception e) {
            logger.error("获取音乐信息时发生错误", e);
            return null;
        }
    }

    /**
     * 下载文件到临时目录
     */
    private String downloadFile(String fileUrl, String fileName) {
        try {
            URL url = new URL(fileUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                logger.error("下载文件失败，响应码: {}", responseCode);
                return null;
            }

            // 确保音频目录存在
            Files.createDirectories(Paths.get(AudioUtils.AUDIO_PATH));

            // 将文件保存到音频目录
            File outputFile = new File(AudioUtils.AUDIO_PATH, fileName);
            
            // 下载文件
            java.nio.file.Files.copy(
                connection.getInputStream(),
                outputFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
            
            return outputFile.getAbsolutePath();
        } catch (Exception e) {
            logger.error("下载文件时发生错误", e);
            return null;
        }
    }

    /**
     * 解析LRC格式歌词
     */
    private List<LyricLine> parseLyrics(String lyricUrl) {
        List<LyricLine> result = new ArrayList<>();
        
        if (lyricUrl == null || lyricUrl.isEmpty()) {
            return result;
        }
        
        try {
            URL url = new URL(lyricUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                logger.error("获取歌词失败，响应码: {}", responseCode);
                return result;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            String line;
            
            // LRC时间标签正则表达式: [mm:ss.xx]
            Pattern pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2})\\](.*)");
            
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    int minutes = Integer.parseInt(matcher.group(1));
                    int seconds = Integer.parseInt(matcher.group(2));
                    int hundredths = Integer.parseInt(matcher.group(3));
                    
                    // 计算毫秒时间
                    long timeMs = (minutes * 60 * 1000) + (seconds * 1000) + (hundredths * 10);
                    String text = matcher.group(4).trim();
                    
                    result.add(new LyricLine(timeMs, text));
                }
            }
            reader.close();
            
            // 按时间排序
            result.sort(Comparator.comparingLong(a -> a.timeMs));
            
        } catch (Exception e) {
            logger.error("解析歌词时发生错误", e);
        }
        
        return result;
    }

}
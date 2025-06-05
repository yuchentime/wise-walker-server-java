package com.xiaozhi.websocket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.utils.OpusProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 音频服务，负责处理音频的非流式发送
 */
@Service
public class AudioService {
    private static final Logger logger = LoggerFactory.getLogger(AudioService.class);

    private static final long OPUS_FRAME_INTERVAL_MS = AudioUtils.OPUS_FRAME_DURATION_MS;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OpusProcessor opusProcessor;

    @Autowired
    private SessionManager sessionManager;

    // 存储每个会话最后一次发送帧的时间戳
    private final Map<String, AtomicLong> lastFrameSentTime = new ConcurrentHashMap<>();

    // 存储每个会话当前是否正在播放音频
    private final Map<String, AtomicBoolean> isPlaying = new ConcurrentHashMap<>();

    /**
     * 发送TTS开始消息
     */
    public Mono<Void> sendStart(WebSocketSession session) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "tts");
        message.put("state", "start");

        try {
            String json = objectMapper.writeValueAsString(message);
            return session.send(Mono.just(session.textMessage(json)));
        } catch (Exception e) {
            logger.error("发送TTS开始消息失败", e);
            return Mono.empty();
        }
    }

    /**
     * 发送TTS句子开始消息
     */
    public Mono<Void> sendSentenceStart(WebSocketSession session, String text) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "tts");
        message.put("state", "sentence_start");
        message.put("text", text);

        try {
            String json = objectMapper.writeValueAsString(message);
            return session.send(Mono.just(session.textMessage(json)));
        } catch (Exception e) {
            logger.error("发送TTS句子开始消息失败", e);
            return Mono.empty();
        }
    }

    /**
     * 发送停止消息
     */
    public Mono<Void> sendStop(WebSocketSession session) {
        String sessionId = session.getId();
        
        // 检查是否需要关闭会话
        if (sessionManager.isCloseAfterChat(sessionId)) {
            sessionManager.closeSession(sessionId);
            return Mono.empty();
        }
        
        Map<String, Object> message = new HashMap<>();
        message.put("type", "tts");
        message.put("state", "stop");

        try {
            String json = objectMapper.writeValueAsString(message);
            // 标记播放结束
            AtomicBoolean playingState = isPlaying.computeIfAbsent(sessionId, k -> new AtomicBoolean());
            playingState.set(false);
            return session.send(Mono.just(session.textMessage(json)));
        } catch (Exception e) {
            logger.error("发送停止消息失败", e);
            AtomicBoolean playingState = isPlaying.computeIfAbsent(sessionId, k -> new AtomicBoolean());
            playingState.set(false);
            return Mono.empty();
        }
    }

    /**
     * 检查会话是否正在播放音频
     */
    public boolean isPlaying(String sessionId) {
        return isPlaying.containsKey(sessionId) && 
               isPlaying.get(sessionId).get();
    }

    /**
     * 发送音频消息
     * 
     * @param session   WebSocketSession会话
     * @param audioPath 音频文件路径
     * @param text      对应的文本
     * @param isFirst   是否是开始消息
     * @param isLast    是否是结束消息
     * @return 操作完成的Mono
     */
    public Mono<Void> sendAudioMessage(
        WebSocketSession session,
        String audioPath,
        String text,
        boolean isFirst,
        boolean isLast) {
        String sessionId = session.getId();

        // 标记开始播放
        AtomicBoolean playingState = isPlaying.computeIfAbsent(sessionId, k -> new AtomicBoolean(true));
        playingState.set(true);

        // 创建一个 Mono 链来处理整个流程
        Mono<Void> startMono = isFirst ? sendStart(session) : Mono.empty();
        
        if (audioPath == null) {
            // 如果没有音频路径但是结束消息，发送结束标记
            if (isLast) {
                return startMono.then(sendStop(session));
            }
            playingState.set(false);
            return startMono;
        }
        
        // 使用单独的变量存储播放状态引用
        final AtomicBoolean finalPlayingState = playingState;
        
        // 发送句子开始标记
        Mono<Void> sentenceStartMono = sendSentenceStart(session, text);
        
        // 处理音频文件
        Mono<List<byte[]>> processAudioMono = Mono.fromCallable(() -> {
            String fullPath = audioPath;
            File audioFile = new File(fullPath);
            if (!audioFile.exists()) {
                return null;
            }

            List<byte[]> opusFrames;

            if (audioPath.contains(".opus")) {
                // 如果是opus文件，直接读取opus帧数据
                opusFrames = opusProcessor.readOpus(audioFile);
            } else {
                // 如果不是opus文件，按照原来的逻辑处理
                byte[] audioData = AudioUtils.readAsPcm(fullPath);
                // 将PCM转换为Opus帧
                opusFrames = opusProcessor.pcmToOpus(sessionId, audioData);
            }
            
            return opusFrames;
        })
        .subscribeOn(Schedulers.boundedElastic());
        
        // 发送音频帧
        Mono<Void> sendFramesMono = processAudioMono.flatMap(opusFrames -> {
            if (opusFrames == null || opusFrames.isEmpty()) {
                return Mono.empty();
            }

            // 确保播放状态为true
            finalPlayingState.set(true);
            
            // 使用Flux.range创建一个发送序列
            return Flux.range(0, opusFrames.size())
                    // 使用固定间隔发送帧
                    .delayElements(Duration.ofMillis(OPUS_FRAME_INTERVAL_MS))
                    // 确保在boundedElastic调度器上执行，以避免阻塞
                    .publishOn(Schedulers.boundedElastic())
                    // 只有当会话仍在播放时才发送
                    .takeWhile(i -> finalPlayingState.get())
                    // 发送每一帧
                    .flatMap(i -> {
                        // 更新活跃时间
                        sessionManager.updateLastActivity(sessionId);
                        // 发送帧数据
                        byte[] frame = opusFrames.get(i);
                        return sendOpusFrame(session, frame);
                    })
                    .then();
        });
        
        // 发送停止消息（只有在isLast为true时才发送）
        Mono<Void> stopMono = Mono.fromRunnable(() -> {
            finalPlayingState.set(false);
        }).then(isLast ? sendStop(session) : Mono.empty());
        
        // 组合所有操作，确保按顺序执行
        return startMono
                .then(sentenceStartMono)
                .then(sendFramesMono)
                .then(stopMono)
                .doOnError(error -> {
                    finalPlayingState.set(false);
                })
                .onErrorResume(error -> {
                    // 如果发生错误但仍然是结束消息，确保发送stop
                    if (isLast) {
                        return sendStop(session);
                    }
                    return Mono.empty();
                });
    }

    /**
     * 发送Opus帧数据
     */
    public Mono<Void> sendOpusFrame(WebSocketSession session, byte[] opusFrame) {
        String sessionId = session.getId();
        try {
            // 直接发送原始Opus帧数据作为二进制消息
            WebSocketMessage wsMessage = session.binaryMessage(
                    factory -> factory.wrap(opusFrame));
            
            return session.send(Mono.just(wsMessage))
                    .onErrorResume(error -> {
                        // 只有当不是连接关闭错误时才记录日志
                        if (!(error instanceof reactor.netty.channel.AbortedException) ||
                                !error.getMessage().contains("Connection has been closed BEFORE send operation")) {
                            logger.error("发送Opus帧失败", error);
                        } else {
                            // 标记播放已停止
                            AtomicBoolean playingState = isPlaying.get(sessionId);
                            if (playingState != null) {
                                playingState.set(false);
                            }
                        }
                        return Mono.empty();
                    });
        } catch (Exception e) {
            logger.error("创建Opus帧消息失败", e);
            return Mono.empty();
        }
    }

    /**
     * 清理会话资源
     */
    public void cleanupSession(String sessionId) {
        lastFrameSentTime.remove(sessionId);
        isPlaying.remove(sessionId);
        opusProcessor.cleanup(sessionId);
    }
}
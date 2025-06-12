package com.xiaozhi.utils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class OpusToPCMConverter {

    public static byte[] convert(byte[] opusData) {
        Path tempDir = null;
        File tempInput = null;
        File tempOutput = null;
        if (opusData.length > 4) {
            String header = new String(opusData, 0, 4);
            System.out.println("文件头：" + header);
        }
        try {
            // 创建临时目录
            tempDir = Files.createTempDirectory("audio_conversion");

            // 创建临时文件
            tempInput = new File(tempDir.toFile(), "input.webm");
            tempOutput = new File(tempDir.toFile(), "output.pcm");

            // 写入输入文件
            try (FileOutputStream fos = new FileOutputStream(tempInput)) {
                fos.write(opusData);
            }

            // 优化后的 ffmpeg 命令
            ProcessBuilder builder = new ProcessBuilder(
                    "ffmpeg",
                    "-y",
                    "-i", tempInput.getAbsolutePath(),
                    "-vn",                      // 禁用视频
                    "-acodec", "pcm_s16le",     // 音频编解码器
                    "-f", "s16le",              // 输出格式
                    "-ac", "1",                 // 单声道
                    "-ar", "16000",             // 采样率
                    "-sample_fmt", "s16",       // 样本格式
                    tempOutput.getAbsolutePath()
            );

            builder.redirectErrorStream(true);
            Process process = builder.start();

            // 读取并打印 ffmpeg 输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[ffmpeg] " + line);
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("FFmpeg 处理失败，exitCode=" + exitCode);
                System.err.println("FFmpeg 输出：\n" + output.toString());
                return null;
            }

            // 检查输出文件是否存在且有内容
            if (!tempOutput.exists() || tempOutput.length() == 0) {
                System.err.println("输出文件不存在或为空");
                return null;
            }

            System.out.println("FFmpeg 处理成功，输出文件大小：" + tempOutput.length() + " bytes");

            // 读取并验证 PCM 数据
            byte[] pcmData = Files.readAllBytes(tempOutput.toPath());
            System.out.println("读取到 PCM 数据：" + pcmData.length + " bytes");

            return pcmData;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            // 清理临时文件
            if (tempInput != null) tempInput.delete();
            if (tempOutput != null) tempOutput.delete();
            if (tempDir != null) {
                try {
                    Files.deleteIfExists(tempDir);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}


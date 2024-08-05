package me.altier.classicvideoplayer;

import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avcodec.AVFrame;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avformat;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.ffmpeg.global.swscale;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Environment;

import java.io.File;
import java.nio.ByteBuffer;

public class VideoPlayer {

    public void playVideo(String filePath) {
        // Initialize FFmpeg
        avcodec.avcodec_register_all();
        avformat.avformat_network_init();

        // Open the video file
        AVFormatContext formatContext = avformat.avformat_alloc_context();
        int result = avformat.avformat_open_input(formatContext, filePath, null, null);
        if (result < 0) {
            // Handle error
            return;
        }

        // Find video and audio streams
        AVStream videoStream = null;
        AVStream audioStream = null;
        for (int i = 0; i < formatContext.nb_streams(); i++) {
            AVStream stream = formatContext.streams(i);
            if (stream.codecpar().codec_type() == avutil.AVMEDIA_TYPE_VIDEO) {
                videoStream = stream;
            } else if (stream.codecpar().codec_type() == avutil.AVMEDIA_TYPE_AUDIO) {
                audioStream = stream;
            }
        }

        if (videoStream == null) {
            // Handle error
            return;
        }

        // Create decoders for video and audio
        AVCodecContext videoCodecContext = avcodec.avcodec_alloc_context3(null);
        avcodec.avcodec_parameters_to_context(videoCodecContext, videoStream.codecpar());
        avcodec.avcodec_open2(videoCodecContext, avcodec.avcodec_find_decoder(videoCodecContext.codec_id()), (AVDictionary) null);

        AVCodecContext audioCodecContext = avcodec.avcodec_alloc_context3(null);
        avcodec.avcodec_parameters_to_context(audioCodecContext, audioStream.codecpar());
        avcodec.avcodec_open2(audioCodecContext, avcodec.avcodec_find_decoder(audioCodecContext.codec_id()), (AVDictionary) null);

        // Initialize MediaCodec for video rendering
        MediaCodec mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoCodecContext.width(), videoCodecContext.height());
        mediaCodec.configure(mediaFormat, null, null, 0);
        mediaCodec.start();

        AVPacket packet = new AVPacket();
        AVFrame frame = avutil.av_frame_alloc();
        while (avformat.av_read_frame(formatContext, packet) >= 0) {
            if (packet.stream_index() == videoStream.index()) {
                // Decode video frame using FFmpeg
                result = avcodec.avcodec_send_packet(videoCodecContext, packet);
                if (result < 0) {
                    // Handle error
                    continue;
                }

                result = avcodec.avcodec_receive_frame(videoCodecContext, frame);
                if (result == avutil.AVERROR_EAGAIN() || result == avutil.AVERROR_EOF()) {
                    continue;
                } else if (result < 0) {
                    // Handle error
                    break;
                }

                // Feed decoded video data to Android MediaCodec
                int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
                    inputBuffer.clear();
                    inputBuffer.put(frame.data(0).position(0).limit(frame.linesize(0)));
                    mediaCodec.queueInputBuffer(inputBufferIndex, 0, frame.linesize(0), frame.pts() * 1000, 0);
                }

                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                while (outputBufferIndex >= 0) {
                    mediaCodec.releaseOutputBuffer(outputBufferIndex, true);
                    outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                }
            }
            avcodec.av_packet_unref(packet);
        }

        // Release resources
        avformat.avformat_close_input(formatContext);
        avcodec.avcodec_free_context(videoCodecContext);
        avcodec.avcodec_free_context(audioCodecContext);
        mediaCodec.stop();
        mediaCodec.release();
    }

    public static void main(String[] args) {
        VideoPlayer player = new VideoPlayer();
        player.playVideo("udp://your_udp_stream_url");
    }
}

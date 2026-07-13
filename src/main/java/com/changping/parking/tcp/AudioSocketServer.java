package com.changping.parking.tcp;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;

@Slf4j
@Component
public class AudioSocketServer implements CommandLineRunner {

    @Value("${freeswitch.socket.port:8084}")
    private int port;

    private final AudioSocketByteHandler audioSocketByteHandler;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture channelFuture;

    public AudioSocketServer(AudioSocketByteHandler audioSocketByteHandler) {
        this.audioSocketByteHandler = audioSocketByteHandler;
    }

    @Override
    public void run(String... args) {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(audioSocketByteHandler);
                    }
                });

        channelFuture = bootstrap.bind(port).addListener(future -> {
            if (future.isSuccess()) {
                log.info("AudioSocketServer 启动成功，监听端口: {}", port);
            } else {
                log.error("AudioSocketServer 启动失败", future.cause());
            }
        });
    }

    @PreDestroy
    public void destroy() {
        log.info("关闭 AudioSocketServer...");
        if (channelFuture != null) {
            channelFuture.channel().close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }
}

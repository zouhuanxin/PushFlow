# PushFlow
android推流和本地mediamuxer合成音视频解决方案（本地采用Camera+AudioRecord实现视频和声音采集，用librtmp库实现推流）

(https://github.com/gongluck/AnalysisAVP)[H264 AVC相关音视频资料]

## 正文

#### 程序功能点
- 摄像头麦克风数据合成音视频
- 屏幕录像直播
- 摄像头录像直播

##### 摄像头麦克风数据合成音视频
MainActivity

##### 屏幕录像直播
MainActivity2

##### 摄像头录像直播
MainActivity3

#### 程序技术点
- MediaMuxer合成音视频
- MediaCodec编码
- AudioRecord声音采集
- librtmp推流
- AVC格式
- AAV格式
- RTMP协议

##### MediaMuxer介绍
该类主要用于将音频和视频进行混合生成多媒体文件，创建该类对象，需要传入输出的文件位置以及格式。
创建
```java
 MediaMuxer mMuxer = new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
```
添加视频
```java
mVideoTrackIndex = mMuxer.addTrack(mediaFormat);
```
添加音频
```java
 mAudioTrackIndex = mMuxer.addTrack(mediaFormat);
```
开始合成
```java
mMuxer.writeSampleData(track, outputBuffer, bufferInfo);
```
这里注意以上我们传入的bufferInfo都是我们通过mediacodec编码以后都数据，原始数据通过mediacodec编码成aac数据（h264），然后交给mediamuxer进行写入。音频也是如此。
ps：使用过程中我们需要注意传入都音轨和视频都pts声音一定要注意，不然合成以后会出现声音播放，视频卡在第一帧，时长非正常都现象。


##### MediaCodec介绍
这是一个编解码器，主要就是把我们的原始音视频数据编码或者解码成特定格式，一个中间件，使用的时候需要配合MediaFormat来配置。

视频MediaFormat配置信息
```java
MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
//色彩空间
format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
//码率
format.setInteger(MediaFormat.KEY_BIT_RATE, 500_000);
//帧率
format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
//关键帧间隔
format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
```

音频MediaFormat配置信息
```java
mMediaFormat = MediaFormat.createAudioFormat(mime, sampleRate, channelCount);
mMediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
mMediaFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
mMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, rate);
```
配置完成以后mediacodec初始化以后加载配置信息，然后在合适的时候启动它。
什么时候启动合适？
如果你只是合成一个音频或者视频可以创建以后立即启动，但是如果你需要合成音视频那注意MediaMuxer必须在添加完视频和音轨以后再启动。
```java
VideomediaCodec = MediaCodec.createEncoderByType("video/avc");
VideomediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
```
ps：每次用完以后记得releaseOutputBuffer释放。

##### rtmp推流介绍
- 以摄像头和麦克风推流为例，简答说明一下推流实现，我们利用摄像头和麦克风获取到了我们到视频和音频数据，然后通过mediacodec进行编码（原始数据转成avc，aac）数据，然后我们通过librtmp库进行推流。在 - 我们通过编码以后我们就已经完成了获取数据操作，下一步我们就应该把我们获取到到数据根据rtmp协议进行封包，音频封包，音频封包很简单，我们只需要在头部增加一个信息，告诉服务器你的音频的格式，轨道，以 - 及是否是编码的数据等，这些数据我们添加在头部发送给服务器就可以。然后视频怎么封包呢？我们的视频文件头部存在一个pps和sps帧，这俩个是在一起的，我们获取到pps和sps帧，然后保存起来，并把我们到视频 
- 信息添加进去，在我们读取到关键帧并发送关键帧之前发送一次pps和sps帧给服务器就可以，其他非关键帧直接发送。所以这里视频比音频要复杂点，总结一下就是获取pps和sps，添加头部信息，每次关键帧发送之前
- 先发送pps和sps，其他非关键帧正常发送。


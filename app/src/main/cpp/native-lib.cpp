#include <jni.h>
#include <string>
#include <android/log.h>
#include <malloc.h>
#include "librtmp/log.h"

extern "C" {
#include "librtmp/rtmp.h"
}
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,"DDDDDD",__VA_ARGS__)
typedef struct {
    int16_t sps_len;
    int16_t pps_len;
    int8_t *sps;
    int8_t *pps;
    RTMP *rtmp;
} Live;
Live *live = nullptr;

RTMP *rtmp_read = 0;

int sendPacket(RTMPPacket *packet) {
    int r = RTMP_SendPacket(live->rtmp, packet, 1);
    RTMPPacket_Free(packet);
    free(packet);
    return r;
}

void prepareVideo(int8_t *data, int len, Live *live) {
    LOGI("prepareVideo");
    for (int i = 0; i < len; i++) {
        //0x00 0x00 0x00 0x01
        if (i + 4 < len) {
            if (data[i] == 0x00 && data[i + 1] == 0x00
                && data[i + 2] == 0x00
                && data[i + 3] == 0x01) {
                //0x00 0x00 0x00 0x01 7 sps 0x00 0x00 0x00 0x01 8 pps
                //将sps pps分开
                //找到pps
                LOGI("将sps pps分开");
                if (data[i + 4] == 0x68) {
                    //去掉界定符
                    live->sps_len = i - 4;
                    live->sps = static_cast<int8_t *>(malloc(live->sps_len));
                    memcpy(live->sps, data + 4, live->sps_len);

                    live->pps_len = len - (4 + live->sps_len) - 4;
                    live->pps = static_cast<int8_t *>(malloc(live->pps_len));
                    memcpy(live->pps, data + 4 + live->sps_len + 4, live->pps_len);
                    LOGI("sps:%d pps:%d", live->sps_len, live->pps_len);
                    break;
                }
            }
        }
    }
}

RTMPPacket *createVideoPackage(int8_t *buf, int len, const long tms, Live *live) {
//    分隔符被抛弃了      --buf指的是651
    buf += 4;
    len -= 4;
    int body_size = len + 9;
    RTMPPacket *packet = (RTMPPacket *) malloc(sizeof(RTMPPacket));
    RTMPPacket_Alloc(packet, len + 9);

    packet->m_body[0] = 0x27;
    if (buf[0] == 0x65) { //关键帧
        packet->m_body[0] = 0x17;
    }
    packet->m_body[1] = 0x01;
    packet->m_body[2] = 0x00;
    packet->m_body[3] = 0x00;
    packet->m_body[4] = 0x00;
    //长度
    packet->m_body[5] = (len >> 24) & 0xff;
    packet->m_body[6] = (len >> 16) & 0xff;
    packet->m_body[7] = (len >> 8) & 0xff;
    packet->m_body[8] = (len) & 0xff;

    //数据
    memcpy(&packet->m_body[9], buf, len);


    packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nBodySize = body_size;
    packet->m_nChannel = 0x04;
    packet->m_nTimeStamp = tms;
    packet->m_hasAbsTimestamp = 0;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nInfoField2 = live->rtmp->m_stream_id;
    return packet;

}

RTMPPacket *createVideoPackage(Live *live) {
    int body_size = 13 + live->sps_len + 3 + live->pps_len;
    RTMPPacket *packet = (RTMPPacket *) malloc(sizeof(RTMPPacket));
    RTMPPacket_Alloc(packet, body_size);
    int i = 0;
    //AVC sequence header 与IDR一样
    packet->m_body[i++] = 0x17;
    //AVC sequence header 设置为0x00
    packet->m_body[i++] = 0x00;
    //CompositionTime
    packet->m_body[i++] = 0x00;
    packet->m_body[i++] = 0x00;
    packet->m_body[i++] = 0x00;
    //AVC sequence header
    packet->m_body[i++] = 0x01;   //configurationVersion 版本号 1
    packet->m_body[i++] = live->sps[1]; //profile 如baseline、main、 high
    packet->m_body[i++] = live->sps[2]; //profile_compatibility 兼容性
    packet->m_body[i++] = live->sps[3]; //profile level
    packet->m_body[i++] = 0xFF; // reserved（111111） + lengthSizeMinusOne（2位 nal 长度） 总是0xff
    //sps
    packet->m_body[i++] = 0xE1; //reserved（111） + lengthSizeMinusOne（5位 sps 个数） 总是0xe1
    //sps length 2字节
    packet->m_body[i++] = (live->sps_len >> 8) & 0xff; //第0个字节
    packet->m_body[i++] = live->sps_len & 0xff;        //第1个字节
    memcpy(&packet->m_body[i], live->sps, live->sps_len);
    i += live->sps_len;

    /*pps*/
    packet->m_body[i++] = 0x01; //pps number
    //pps length
    packet->m_body[i++] = (live->pps_len >> 8) & 0xff;
    packet->m_body[i++] = live->pps_len & 0xff;
    memcpy(&packet->m_body[i], live->pps, live->pps_len);

    packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nBodySize = body_size;
    packet->m_nChannel = 0x04;
    packet->m_nTimeStamp = 0;
    packet->m_hasAbsTimestamp = 0;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nInfoField2 = live->rtmp->m_stream_id;
    return packet;
}

int sendVideo(int8_t *buf, int len, long tms) {
    int ret;
    //buf[4] == 0x67
    if ((buf[4] & 0x1F) == 7) {//sps pps
        if (live && (!live->pps || !live->sps)) {
            prepareVideo(buf, len, live);
        }
    } else {
        //buf[4] == 0x65
        if ((buf[4] & 0x1F) == 5) {//关键帧 I 帧
            RTMPPacket *packet = createVideoPackage(live);//发送sps pps
            if (!(ret = sendPacket(packet))) {
            }
        }
        RTMPPacket *packet = createVideoPackage(buf, len, tms, live);
        ret = sendPacket(packet);
    }
    return ret;
}

RTMPPacket *createAudioPacket(int8_t *buf, const int len, const int type, const long tms,
                              Live *live) {

//    组装音频包  两个字节    是固定的   af    如果是第一次发  你就是 01       如果后面   00  或者是 01  aac
    int body_size = len + 2;
    RTMPPacket *packet = (RTMPPacket *) malloc(sizeof(RTMPPacket));
    RTMPPacket_Alloc(packet, body_size);
//         音频头
    packet->m_body[0] = 0xAF;
    if (type == 1) {
//        头
        packet->m_body[1] = 0x00;
    } else {
        packet->m_body[1] = 0x01;
    }
    memcpy(&packet->m_body[2], buf, len);
    packet->m_packetType = RTMP_PACKET_TYPE_AUDIO;
    packet->m_nChannel = 0x05;
    packet->m_nBodySize = body_size;
    packet->m_nTimeStamp = tms;
    packet->m_hasAbsTimestamp = 0;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nInfoField2 = live->rtmp->m_stream_id;
    return packet;
}

int sendAudio(int8_t *buf, int len, int type, int tms) {
//    创建音频包   如何组装音频包
    RTMPPacket *packet = createAudioPacket(buf, len, type, tms, live);
    int ret = sendPacket(packet);
    return ret;
}


extern "C" JNIEXPORT jstring JNICALL
Java_com_demo_pushflow_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_demo_pushflow_CameraLive_connect(JNIEnv *env, jobject thiz, jstring url_) {

    // 首先 Java 的转成 C 的字符串，不然无法使用
    const char *url = env->GetStringUTFChars(url_, 0);
    int ret;
    do {
        live = (Live *) malloc(sizeof(Live));
        memset(live, 0, sizeof(Live));
        live->rtmp = RTMP_Alloc();// Rtmp 申请内存
        RTMP_Init(live->rtmp);
        live->rtmp->Link.timeout = 30;// 设置 rtmp 初始化参数，比如超时时间、url
        LOGI("connect %s", url);
        if (!(ret = RTMP_SetupURL(live->rtmp, (char *) url))) break;
        RTMP_EnableWrite(live->rtmp);// 开启 Rtmp 写入
        LOGI("RTMP_Connect");
        if (!(ret = RTMP_Connect(live->rtmp, 0))) break;
        LOGI("RTMP_ConnectStream ");
        if (!(ret = RTMP_ConnectStream(live->rtmp, 0))) break;
        LOGI("connect success");
    } while (0);
    if (!ret && live) {
        free(live);
        live = nullptr;
    }

    env->ReleaseStringUTFChars(url_, url);
    return ret;

}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_demo_pushflow_CameraLive_sendData(JNIEnv *env, jobject thiz, jbyteArray data_, jint len,
                                           jlong tms, jint type) {
    int ret;
    jbyte *data = env->GetByteArrayElements(data_, NULL);
    switch (type) {
        case 0: //video
            //LOGI("send video  lenght :%d", len);
            ret = sendVideo(data, len, tms);
            break;
        default: //audio
            ret = sendAudio(data, len, type, tms);
            //LOGI("send Audio  lenght :%d", len);
            break;
    }
    env->ReleaseByteArrayElements(data_, data, 0);
    return ret;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_demo_pushflow_CameraLive_disconnect(JNIEnv *env, jobject thiz) {
    if (live) {
        if (live->rtmp) {
            RTMP_Close(live->rtmp);
            RTMP_Free(live->rtmp);
        }
        if (live->sps) {
            free(live->sps);
        }
        if (live->pps) {
            free(live->pps);
        }
        free(live);
        live = 0;
        if (rtmp_read){
            RTMP_Close(rtmp_read);
            RTMP_Free(rtmp_read);
        }
    }
}


extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_demo_pushflow_CameraLive_readData(JNIEnv *env, jobject thiz) {
    if (!rtmp_read){
        rtmp_read=RTMP_Alloc();
        RTMP_Init(rtmp_read);
        //set connection timeout,default 30s
        rtmp_read->Link.timeout=10;
        // HKS's live URL
        if(!RTMP_SetupURL(rtmp_read,"rtmp://192.168.123.196/live/livestrea2")){
            RTMP_Log(RTMP_LOGERROR,"SetupURL Err\n");
            RTMP_Free(rtmp_read);
        }
        //1hour
        RTMP_SetBufferMS(rtmp_read, 3600*1000);
        if(!RTMP_Connect(rtmp_read,NULL)){
            RTMP_Log(RTMP_LOGERROR,"Connect Err\n");
            RTMP_Free(rtmp_read);
        }
        if(!RTMP_ConnectStream(rtmp_read,0)){
            RTMP_Log(RTMP_LOGERROR,"ConnectStream Err\n");
            RTMP_Close(rtmp_read);
            RTMP_Free(rtmp_read);
        }
    }

    int nRead=0;
    int bufsize=1024*1024;
    char* buf=(char*)malloc(bufsize);
    memset(buf,0,bufsize);
    nRead=RTMP_Read(rtmp_read,buf,bufsize);
    LOGI("bufsize : %d",bufsize);
    char arr[bufsize];
    strcpy(arr, buf);
    memcpy(arr, buf, sizeof(arr));

    jbyteArray byteArray = env->NewByteArray(bufsize);
    env->SetByteArrayRegion(byteArray, 0, bufsize, (jbyte *) arr);

    return byteArray;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_demo_pushflow_ScreenLive_connect(JNIEnv *env, jobject thiz, jstring url_) {

    // 首先 Java 的转成 C 的字符串，不然无法使用
    const char *url = env->GetStringUTFChars(url_, 0);
    int ret;
    do {
        live = (Live *) malloc(sizeof(Live));
        memset(live, 0, sizeof(Live));
        live->rtmp = RTMP_Alloc();// Rtmp 申请内存
        RTMP_Init(live->rtmp);
        live->rtmp->Link.timeout = 10;// 设置 rtmp 初始化参数，比如超时时间、url
        LOGI("connect %s", url);
        if (!(ret = RTMP_SetupURL(live->rtmp, (char *) url))) break;
        RTMP_EnableWrite(live->rtmp);// 开启 Rtmp 写入
        LOGI("RTMP_Connect");
        if (!(ret = RTMP_Connect(live->rtmp, 0))) break;
        LOGI("RTMP_ConnectStream ");
        if (!(ret = RTMP_ConnectStream(live->rtmp, 0))) break;
        LOGI("connect success");
    } while (0);
    if (!ret && live) {
        free(live);
        live = nullptr;
    }

    env->ReleaseStringUTFChars(url_, url);
    return ret;

}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_demo_pushflow_ScreenLive_sendData(JNIEnv *env, jobject thiz, jbyteArray data_, jint len,
                                           jlong tms, jint type) {
    int ret;
    jbyte *data = env->GetByteArrayElements(data_, NULL);
    switch (type) {
        case 0: //video
            LOGI("send video  lenght :%d", len);
            ret = sendVideo(data, len, tms);
            break;
        default: //audio
            ret = sendAudio(data, len, type, tms);
            LOGI("send Audio  lenght :%d", len);
            break;
    }
    env->ReleaseByteArrayElements(data_, data, 0);
    return ret;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_demo_pushflow_ScreenLive_disconnect(JNIEnv *env, jobject thiz) {
    if (live) {
        if (live->rtmp) {
            RTMP_Close(live->rtmp);
            RTMP_Free(live->rtmp);
        }
        if (live->sps) {
            free(live->sps);
        }
        if (live->pps) {
            free(live->pps);
        }
        free(live);
        live = 0;
    }
}

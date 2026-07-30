#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "sherpa-onnx-c-api.h"

typedef struct {
  const SherpaOnnxOnlineRecognizer *recognizer;
  const SherpaOnnxOnlineStream *stream;
} CaptionSession;

static CaptionSession *from_handle(jlong handle) {
  return (CaptionSession *)(intptr_t)handle;
}

JNIEXPORT jlong JNICALL
Java_com_zuoqirun_lyricscompanion_SherpaBilingualRecognizer_nativeOpen(
    JNIEnv *env, jclass clazz, jstring encoder, jstring decoder, jstring joiner,
    jstring tokens) {
  (void)clazz;
  const char *encoder_path = (*env)->GetStringUTFChars(env, encoder, NULL);
  const char *decoder_path = (*env)->GetStringUTFChars(env, decoder, NULL);
  const char *joiner_path = (*env)->GetStringUTFChars(env, joiner, NULL);
  const char *tokens_path = (*env)->GetStringUTFChars(env, tokens, NULL);
  if (!encoder_path || !decoder_path || !joiner_path || !tokens_path) goto cleanup;

  SherpaOnnxOnlineRecognizerConfig config;
  memset(&config, 0, sizeof(config));
  config.feat_config.sample_rate = 16000;
  config.feat_config.feature_dim = 80;
  config.model_config.transducer.encoder = encoder_path;
  config.model_config.transducer.decoder = decoder_path;
  config.model_config.transducer.joiner = joiner_path;
  config.model_config.tokens = tokens_path;
  config.model_config.provider = "cpu";
  config.model_config.num_threads = 2;
  config.decoding_method = "greedy_search";
  config.enable_endpoint = 1;
  config.rule1_min_trailing_silence = 1.2f;
  config.rule2_min_trailing_silence = 0.8f;
  config.rule3_min_utterance_length = 18.0f;

  CaptionSession *session = (CaptionSession *)calloc(1, sizeof(CaptionSession));
  if (!session) goto cleanup;
  session->recognizer = SherpaOnnxCreateOnlineRecognizer(&config);
  if (!session->recognizer) { free(session); goto cleanup; }
  session->stream = SherpaOnnxCreateOnlineStream(session->recognizer);
  if (!session->stream) {
    SherpaOnnxDestroyOnlineRecognizer(session->recognizer);
    free(session);
    goto cleanup;
  }
  (*env)->ReleaseStringUTFChars(env, encoder, encoder_path);
  (*env)->ReleaseStringUTFChars(env, decoder, decoder_path);
  (*env)->ReleaseStringUTFChars(env, joiner, joiner_path);
  (*env)->ReleaseStringUTFChars(env, tokens, tokens_path);
  return (jlong)(intptr_t)session;

cleanup:
  if (encoder_path) (*env)->ReleaseStringUTFChars(env, encoder, encoder_path);
  if (decoder_path) (*env)->ReleaseStringUTFChars(env, decoder, decoder_path);
  if (joiner_path) (*env)->ReleaseStringUTFChars(env, joiner, joiner_path);
  if (tokens_path) (*env)->ReleaseStringUTFChars(env, tokens, tokens_path);
  return 0;
}

JNIEXPORT void JNICALL
Java_com_zuoqirun_lyricscompanion_SherpaBilingualRecognizer_nativeAccept(
    JNIEnv *env, jclass clazz, jlong handle, jfloatArray audio, jint count) {
  (void)clazz;
  CaptionSession *session = from_handle(handle);
  if (!session || !session->stream || count <= 0) return;
  jfloat *samples = (*env)->GetFloatArrayElements(env, audio, NULL);
  if (!samples) return;
  SherpaOnnxOnlineStreamAcceptWaveform(session->stream, 16000, samples, count);
  (*env)->ReleaseFloatArrayElements(env, audio, samples, JNI_ABORT);
  while (SherpaOnnxIsOnlineStreamReady(session->recognizer, session->stream)) {
    SherpaOnnxDecodeOnlineStream(session->recognizer, session->stream);
  }
}

JNIEXPORT jstring JNICALL
Java_com_zuoqirun_lyricscompanion_SherpaBilingualRecognizer_nativeText(
    JNIEnv *env, jclass clazz, jlong handle) {
  (void)clazz;
  CaptionSession *session = from_handle(handle);
  if (!session || !session->stream) return (*env)->NewStringUTF(env, "");
  const SherpaOnnxOnlineRecognizerResult *result =
      SherpaOnnxGetOnlineStreamResult(session->recognizer, session->stream);
  if (!result) return (*env)->NewStringUTF(env, "");
  jstring text = (*env)->NewStringUTF(env, result->text ? result->text : "");
  SherpaOnnxDestroyOnlineRecognizerResult(result);
  return text;
}

JNIEXPORT jboolean JNICALL
Java_com_zuoqirun_lyricscompanion_SherpaBilingualRecognizer_nativeIsEndpoint(
    JNIEnv *env, jclass clazz, jlong handle) {
  (void)env; (void)clazz;
  CaptionSession *session = from_handle(handle);
  return session && session->stream &&
      SherpaOnnxOnlineStreamIsEndpoint(session->recognizer, session->stream) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_zuoqirun_lyricscompanion_SherpaBilingualRecognizer_nativeReset(
    JNIEnv *env, jclass clazz, jlong handle) {
  (void)env; (void)clazz;
  CaptionSession *session = from_handle(handle);
  if (session && session->stream) SherpaOnnxOnlineStreamReset(session->recognizer, session->stream);
}

JNIEXPORT void JNICALL
Java_com_zuoqirun_lyricscompanion_SherpaBilingualRecognizer_nativeClose(
    JNIEnv *env, jclass clazz, jlong handle) {
  (void)env; (void)clazz;
  CaptionSession *session = from_handle(handle);
  if (!session) return;
  if (session->stream) SherpaOnnxDestroyOnlineStream(session->stream);
  if (session->recognizer) SherpaOnnxDestroyOnlineRecognizer(session->recognizer);
  free(session);
}

#include <jni.h>
#include <string>
#include <vector>
#include "llama.cpp/include/llama.h"

static llama_model* model = nullptr;
static llama_context* ctx = nullptr;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_seunome_mcaddonai_LlamaBridge_loadModel(JNIEnv* env, jobject, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    llama_backend_init();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;

    model = llama_load_model_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!model) return JNI_FALSE;

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 4096;
    ctx_params.n_threads = 4;

    ctx = llama_new_context_with_model(model, ctx_params);
    return ctx != nullptr ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_seunome_mcaddonai_LlamaBridge_clearContextMemory(JNIEnv*, jobject) {
    if (ctx) {
        llama_kv_cache_clear(ctx);
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_seunome_mcaddonai_LlamaBridge_runInference(JNIEnv* env, jobject, jstring promptJStr) {
    if (!ctx || !model) return env->NewStringUTF("// Erro: Modelo IA nao carregado.");

    const char* prompt = env->GetStringUTFChars(promptJStr, nullptr);
    std::string promptStr(prompt);
    env->ReleaseStringUTFChars(promptJStr, prompt);

    std::vector<llama_token> tokens(promptStr.length() + 16);
    int n_tokens = llama_tokenize(model, promptStr.c_str(), promptStr.length(), tokens.data(), tokens.size(), true, false);
    tokens.resize(n_tokens);

    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    llama_decode(ctx, batch);

    std::string result = "";
    llama_sampler* smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.2f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(1234));

    for (int i = 0; i < 2048; i++) {
        llama_token new_token = llama_sampler_sample(smpl, ctx, -1);
        if (llama_token_is_eog(model, new_token)) break;

        char buf[128];
        int n = llama_token_to_piece(model, new_token, buf, sizeof(buf), 0, true);
        if (n > 0) result.append(buf, n);

        llama_batch next_batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(ctx, next_batch) != 0) break;
    }

    llama_sampler_free(smpl);
    return env->NewStringUTF(result.c_str());
}

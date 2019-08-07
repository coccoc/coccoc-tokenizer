#include <jni.h>
#include <iostream>
#include <cstdint>
#include <vector>
#include <cassert>
#include <tokenizer/tokenizer.hpp>
#include "com_coccoc_Tokenizer.h"

JNIEXPORT jlong JNICALL Java_com_coccoc_Tokenizer_segmentPointer(
	JNIEnv *env, jobject obj, jstring jni_text, jboolean for_transforming, jint tokenize_option)
{
	// Use shared-memory instead of message-passing mechanism to transfer data to Java
	// return a pointer to an array of pointers

	const jchar *jtext = env->GetStringCritical(jni_text, nullptr);
	int text_length = env->GetStringLength(jni_text);
	std::vector< uint32_t > *text = new std::vector< uint32_t >();
	text->reserve(text_length);

	std::vector< int > original_pos;
	Tokenizer::instance().normalize_for_tokenization(jtext, text_length, *text, original_pos, true);
	env->ReleaseStringCritical(jni_text, jtext);

	// use pointer to avoid automatic deallocation
	std::vector< Token > *ranges = new std::vector< Token >();
	std::vector< int > *space_positions = new std::vector< int >();

	Tokenizer::instance().handle_tokenization_request< Token >(
		*text, *ranges, *space_positions, original_pos, for_transforming, tokenize_option);
	for (size_t i = 0; i < ranges->size(); ++i)
	{
		ranges->at(i).original_start += original_pos[ranges->at(i).normalized_start];
		ranges->at(i).original_end += original_pos[ranges->at(i).normalized_end];
	}

	int64_t *res_pointer = new int64_t[8];
	res_pointer[0] = (int64_t) text;
	res_pointer[1] = (int64_t) text->data(); // pointer to normalized string
	res_pointer[2] = (int64_t) ranges->size();
	res_pointer[3] = (int64_t) ranges->data(); // pointer to raw data array inside vector
	res_pointer[4] = (int64_t) ranges;	 // pointer to actual vector
	res_pointer[5] = (int64_t) space_positions->size();
	res_pointer[6] = (int64_t) space_positions->data();
	res_pointer[7] = (int64_t) space_positions;
	return (jlong) res_pointer;
}

JNIEXPORT void JNICALL Java_com_coccoc_Tokenizer_freeMemory(JNIEnv *env, jobject obj, jlong res_pointer)
{
	// Cast each object pointer to their respective type, must be careful
	int64_t *p = (int64_t *) res_pointer;
	delete (std::vector< uint32_t > *) (p[0]);
	delete (std::vector< Token > *) (p[4]);
	delete (std::vector< int > *) (p[7]);
	delete[](int64_t *) p;
}

JNIEXPORT jint JNICALL Java_com_coccoc_Tokenizer_initialize(JNIEnv *env, jobject obj, jstring jni_dict_path)
{
	const char *dict_path = env->GetStringUTFChars(jni_dict_path, nullptr);
	if (0 > Tokenizer::instance().initialize(std::string(dict_path))) return -1;
	env->ReleaseStringUTFChars(jni_dict_path, dict_path);
	return 0;
}

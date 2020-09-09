#include "tokenizer.h"
#include <tokenizer/tokenizer.hpp>
#include <vector>
#include <iostream>
#include <inttypes.h>
long long segmentPointer(const char *go_text, bool for_transforming, int tokenize_option, bool keep_puncts, int text_length) {
    std::string original_text = std::string(go_text);
	std::vector< uint32_t > *text = new std::vector<uint32_t>();
	std::vector< int > original_pos;
    
	Tokenizer::instance().normalize_for_tokenization(original_text, *text, original_pos);
	std::vector< Token > *ranges = new std::vector< Token >();
	std::vector< int > *space_positions = new std::vector< int >();
	Tokenizer::instance().handle_tokenization_request< Token >(
		*text, *ranges, *space_positions, original_pos, for_transforming, tokenize_option, keep_puncts);
	for (size_t i = 0; i < ranges->size(); ++i)
	{
		ranges->at(i).original_start += original_pos[ranges->at(i).normalized_start];
		ranges->at(i).original_end += original_pos[ranges->at(i).normalized_end];
	}
	int64_t *res_pointer = new int64_t[8];
	res_pointer[0] = (int64_t) text;
	res_pointer[1] = (int64_t) text ->data();
	res_pointer[2] = (int64_t) ranges->size();
	res_pointer[3] = (int64_t) ranges->data(); // pointer to raw data array inside vector
	res_pointer[4] = (int64_t) ranges;	 // pointer to actual vector
	res_pointer[5] = (int64_t) space_positions->size();
	res_pointer[6] = (int64_t) space_positions->data();
	res_pointer[7] = (int64_t) space_positions;
	return (long long) res_pointer;
}
void freeMemory(long long res_pointer) { 
    int64_t *p = (int64_t *) res_pointer;
	delete (std::vector< uint32_t > *) (p[0]);	
	delete (std::vector< Token > *) (p[4]);
	delete (std::vector< int > *) (p[7]);
	delete[](int64_t *) p;

}
int initialize(const char * dict_path) { 
  	if (0 > Tokenizer::instance().initialize(std::string(dict_path))) return -1;
		return 0; 
}

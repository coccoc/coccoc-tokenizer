#ifndef SYLLABLE_HASH_TRIE_NODE_HPP
#define SYLLABLE_HASH_TRIE_NODE_HPP

#include "hash_trie_node.hpp"
#include <vector>

struct SyllableHashTrieNode : HashTrieNode
{
	float weight;
	int length;

	SyllableHashTrieNode() : HashTrieNode()
	{
		this->weight = 0.5;
		this->length = 0;
	}

	void finalize()
	{
		static std::vector< float > self_sticky_params = {
			8.68047, // 0.self_coeff
			1.49414, // 1.self_len_power
			0.02,    // 2.self_power
		};
		weight = self_sticky_params[0] * std::pow(length, self_sticky_params[1]) *
			 std::pow(frequency, self_sticky_params[2]);
	}
};

#endif // SYLLABLE_HASH_TRIE_NODE_HPP
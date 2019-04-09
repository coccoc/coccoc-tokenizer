#ifndef MULTITERM_HASH_TRIE_NODE_HPP
#define MULTITERM_HASH_TRIE_NODE_HPP

#include "hash_trie_node.hpp"

struct MultitermHashTrieNode : HashTrieNode
{
	float weight;
	int space_count;
	bool is_special;

	MultitermHashTrieNode() : HashTrieNode()
	{
		this->weight = 0.5;
		this->space_count = 0;
		this->is_special = false;
	}

	void finalize()
	{
		static float param[9] = {0.38, 1, 0.14, 2.59, 1.42, 4.42, 1.45, 0.23, 0.1};
		float freq_power = param[space_count << 1];
		float len_power = param[space_count << 1 | 1];
		this->weight = pow(log2(frequency + 3.0), freq_power) * pow(space_count + 1, len_power);
	}
};

#endif // MULTITERM_HASH_TRIE_NODE_HPP
#ifndef SYLLABLE_DA_TRIE_NODE_HPP
#define SYLLABLE_DA_TRIE_NODE_HPP

#include "da_trie_node.hpp"
#include "syllable_hash_trie_node.hpp"

struct SyllableDATrieNode : DATrieNode
{
	int index;
	float weight;

	SyllableDATrieNode() : DATrieNode()
	{
		this->index = -1;
		this->weight = 0;
	}

	void assign_data(const SyllableHashTrieNode &node)
	{
		this->weight = node.weight;
		// this->index will be updated later
	}
};

#endif // SYLLABLE_DA_TRIE_NODE_HPP
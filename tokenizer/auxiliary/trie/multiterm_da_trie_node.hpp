#ifndef MULTITERM_DA_TRIE_NODE_HPP
#define MULTITERM_DA_TRIE_NODE_HPP

#include "da_trie_node.hpp"

struct MultitermDATrieNode : DATrieNode
{
	float weight;
	bool is_ending;
	bool is_special;

	MultitermDATrieNode() : DATrieNode()
	{
		this->weight = 0;
		this->is_ending = false;
		this->is_special = false;
	}

	void assign_data(const MultitermHashTrieNode &node)
	{
		this->weight = node.weight;
		this->is_ending = node.frequency != -1;
		this->is_special = node.is_special;
	}
};

#endif // MULTITERM_DA_TRIE_NODE_HPP
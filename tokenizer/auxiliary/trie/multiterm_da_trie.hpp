#ifndef MULTITERM_DA_TRIE_HPP
#define MULTITERM_DA_TRIE_HPP

#include "multiterm_hash_trie.hpp"
#include "multiterm_da_trie_node.hpp"
#include "da_trie.hpp"
#include <vector>
#include <set>
#include <string>

struct MultitermDATrie : DATrie< MultitermHashTrieNode, MultitermDATrieNode >
{
	MultitermDATrie() : DATrie< MultitermHashTrieNode, MultitermDATrieNode >()
	{
	}
	MultitermDATrie(const std::string &file_path) : DATrie< MultitermHashTrieNode, MultitermDATrieNode >(file_path)
	{
	}
	MultitermDATrie(MultitermHashTrie &multiterm_hash_trie)
	    : DATrie< MultitermHashTrieNode, MultitermDATrieNode >(multiterm_hash_trie)
	{
	}

	inline float get_weight(const int u)
	{
		return pool[u].weight;
	}

	inline bool is_ending(const int u)
	{
		return pool[u].is_ending;
	}

	inline bool is_special(const int u)
	{
		return pool[u].is_special;
	}
};

#endif // MULTITERM_DA_TRIE_HPP
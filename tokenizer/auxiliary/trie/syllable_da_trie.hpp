#ifndef SYLLABLE_DA_TRIE_HPP
#define SYLLABLE_DA_TRIE_HPP

#include <string>
#include <vector>
#include <set>
#include "../utf8.h"
#include "syllable_hash_trie.hpp"
#include "syllable_da_trie_node.hpp"
#include "da_trie.hpp"

struct SyllableDATrie : DATrie< SyllableHashTrieNode, SyllableDATrieNode >
{
	SyllableDATrie() : DATrie< SyllableHashTrieNode, SyllableDATrieNode >()
	{
	}
	SyllableDATrie(const std::string &file_path) : DATrie< SyllableHashTrieNode, SyllableDATrieNode >(file_path)
	{
	}
	SyllableDATrie(SyllableHashTrie &syllable_hash_trie)
	    : DATrie< SyllableHashTrieNode, SyllableDATrieNode >(syllable_hash_trie)
	{
	}

	int update_index(const std::string &s, int index)
	{ // return the length of s if success
		int cur_node = 0;
		utf8::unchecked::iterator< std::string::const_iterator > it(s.begin()), end_it(s.end());
		int length = 0;
		while (it != end_it)
		{
			uint32_t codepoint = *it;
			if (!has_child(cur_node, codepoint))
			{
				return 0;
			}
			cur_node = get_child(cur_node, codepoint);
			it++;
			length++;
		}
		pool[cur_node].index = index;
		return length;
	}

	inline float get_weight(const int u)
	{
		return pool[u].weight;
	}

	inline int get_index(const int u)
	{
		return pool[u].index;
	}
};

#endif // SYLLABLE_DA_TRIE_HPP

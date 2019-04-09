// TODO: better use gperf for this

#ifndef STRING_SET_TRIE_HPP
#define STRING_SET_TRIE_HPP

#include "hash_trie_node.hpp"
#include "hash_trie.hpp"
#include "da_trie_node.hpp"
#include "da_trie.hpp"

struct StringSetTrieNode : DATrieNode
{
	bool is_ending;

	StringSetTrieNode() : DATrieNode()
	{
		this->is_ending = false;
	}

	void assign_data(const HashTrieNode &node)
	{
		this->is_ending = node.frequency != -1;
	}
};

struct StringSetTrie : DATrie< HashTrieNode, StringSetTrieNode >
{
	StringSetTrie(std::initializer_list< const char * > T)
	{
		HashTrie< HashTrieNode > initial_hash_trie;
		for (const char *s : T)
		{
			initial_hash_trie.add_new_term(s, 1);
		}
		build_from_hash_trie(initial_hash_trie);
	}

	bool contains(const uint32_t *text, int length)
	{
		int node = 0;
		for (int i = 0; i < length; ++i)
		{
			if (!has_child(node, text[i])) return false;
			node = cache_node;
		}
		return pool[node].is_ending;
	}
};

#endif // STRING_SET_TRIE_HPP
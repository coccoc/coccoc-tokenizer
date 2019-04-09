#ifndef SYLLABLE_HASH_TRIE_HPP
#define SYLLABLE_HASH_TRIE_HPP

#include <string>
#include "hash_trie.hpp"
#include "syllable_hash_trie_node.hpp"
#include "../utf8.h"

struct SyllableHashTrie : HashTrie< SyllableHashTrieNode >
{
	void add_new_term(const std::string &s, int frequency)
	{
		int end_node = HashTrie< SyllableHashTrieNode >::add_new_term(s, frequency);
		pool[end_node].length = utf8::unchecked::distance(s.begin(), s.end());
	}
};

#endif // SYLLABLE_HASH_TRIE_HPP
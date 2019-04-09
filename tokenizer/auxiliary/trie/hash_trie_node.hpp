#ifndef HASH_TRIE_NODE_HPP
#define HASH_TRIE_NODE_HPP

#include <climits>
#include "../sparsepp/spp.h"

struct HashTrieNode
{
	template < typename K, typename V >
	using compact_map_t = spp::sparse_hash_map< K, V >;

	int frequency;

	compact_map_t< uint32_t, int > children;

	HashTrieNode()
	{
		this->frequency = -1;
	}

	void mark_ending(int frequency)
	{
		if (~this->frequency)
		{
			if (this->frequency < INT_MAX - frequency)
			{
				this->frequency += frequency;
			}
			else
			{
				this->frequency = INT_MAX;
			}
		}
		else
		{
			this->frequency = frequency;
		}
	}

	void finalize()
	{
	}
};

#endif // HASH_TRIE_NODE_HPP
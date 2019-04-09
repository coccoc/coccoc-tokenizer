#ifndef DA_TRIE_NODE_HPP
#define DA_TRIE_NODE_HPP

struct DATrieNode
{
	int base;
	int parent;

	DATrieNode()
	{
		base = 0;
		parent = -1;
	}

	// derived classes must implement assign_data(const HashTrieNode &node);
};

#endif // DA_TRIE_NODE_HPP
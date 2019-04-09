#ifndef MULTITERM_HASH_TRIE_HPP
#define MULTITERM_HASH_TRIE_HPP

#include <string>
#include "multiterm_hash_trie_node.hpp"
#include "hash_trie.hpp"
#include "../vn_lang_tool.hpp"

struct MultitermHashTrie : HashTrie< MultitermHashTrieNode >
{
	void add_new_term(const std::string &s, int frequency, bool add_transformation, bool is_special)
	{
		static auto count_spaces = [](const std::string &s) -> int
		{
			int res = 0;
			for (char c : s)
				res += c == ' ';
			return res;
		};

		int end_node = HashTrie< MultitermHashTrieNode >::add_new_term(s, frequency);
		pool[end_node].space_count = count_spaces(s);
		pool[end_node].is_special = is_special;
		if (add_transformation)
		{
			std::string transformed = VnLangTool::get_transformation_string(s);
			if (s != transformed)
			{
				HashTrie< MultitermHashTrieNode >::add_new_term(transformed, frequency);
			}
		}
	}
};

#endif // MULTITERM_HASH_TRIE_HPP
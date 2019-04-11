#ifndef TOKENIZER_HELPER_HPP
#define TOKENIZER_HELPER_HPP

#include <vector>
#include "token.hpp"
#include "auxiliary/trie/string_set_trie.hpp"
#include "auxiliary/vn_lang_tool.hpp"

namespace Helper
{
StringSetTrie DOMAIN_FIELDs{"com", "net", "org", "info", "gov", "edu", "biz"};
StringSetTrie DOMAIN_ENDs{"com",
	"net",
	"org",
	"info",
	"gov",
	"edu",
	"biz",
	"vn",
	"jp",
	"kr",
	"us",
	"uk",
	"au",
	"sg",
	"cn",
	"ru",
	"pl",
	"ca"};

bool is_digit(char c)
{
	return '0' <= c && c <= '9';
}

inline bool is_domain_field(const uint32_t *text, int from, int to)
{
	return to - from <= 4 && to - from >= 3 && from > 1 && text[from - 1] == '.' &&
	       VnLangTool::is_alphanumeric(text[from - 2]) && DOMAIN_FIELDs.contains(text + from, to - from);
}

inline bool is_domain_end(const uint32_t *text, int from, int to)
{
	return to - from <= 4 && to - from >= 2 && from > 1 && text[from - 1] == '.' &&
	       VnLangTool::is_alphanumeric(text[from - 2]) && DOMAIN_ENDs.contains(text + from, to - from);
}

inline bool az_only(uint32_t c)
{
	return 'a' <= c && c <= 'z';
}

inline bool is_ordinal_suffix(uint32_t a, uint32_t b)
{
	return (a == 't' && b == 'h') || (a == 's' && b == 't') || (a == 'n' && b == 'd') || (a == 'r' && b == 'd');
}

inline bool is_special_operator_sign(uint32_t c)
{
	return c == '^' || c == '+';
}

inline bool is_small_number_or_az_char(const uint32_t *text, const Token &token)
{
	return (token.type == Token::NUMBER && token.normalized_end - token.normalized_start <= 6) ||
	       (token.normalized_end - token.normalized_start == 1 && az_only(text[token.normalized_start]));
}

inline int find_last_space_pos(const uint32_t *text, const Token &token)
{
	for (int i = token.normalized_end - 1; i >= token.normalized_start; --i)
	{
		if (text[i] == ' ') return i;
	}
	return -1;
}

inline bool maximize(double &a, double b)
{
	if (a < b)
	{
		a = b;
		return true;
	}
	return false;
}

bool vector_match_string(const std::vector< uint32_t > &v, const std::string &s, int from = 0)
{
	if (from + s.size() > v.size()) return false;

	for (int i = 0; i < (int) s.size(); ++i)
	{
		if (v[from + i] != (uint32_t) s[i]) return false;
	}
	return true;
}

std::string to_string_range(const std::vector< uint32_t > &text, int left, int right)
{
	std::string res;
	for (int i = left; i < right; ++i)
	{
		res += VnLangTool::lower_char(text[i]);
	}
	return res;
}
}

#endif // TOKENIZER_HELPER_HPP

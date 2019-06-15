#ifndef TOKENIZER_HPP
#define TOKENIZER_HPP

#include <cmath>
#include <climits>
#include <algorithm>
#include <string>
#include <tokenizer/config.h>
#include "auxiliary/vn_lang_tool.hpp"
#include "auxiliary/trie.hpp"
#include "auxiliary/buffered_reader.hpp"
#include "auxiliary/sparsepp/spp.h"
#include "auxiliary/file_serializer.hpp"
#include "helper.hpp"
#include "token.hpp"

class Tokenizer
{
public:
	static const int TOKENIZE_NORMAL = 0;
	static const int TOKENIZE_HOST = 1;
	static const int TOKENIZE_URL = 2;

	// This is quite fast, can change this to something else if we want
	// to reduce memory consumption or improve speed
	// remember to change dict_compiler.cpp also
	typedef spp::sparse_hash_map< int, float > fast_map_t;
	typedef int trie_node_t; // nodes are indexed by non-negative integers in DATrie

	// Note: both Trie saves toned terms
	MultitermDATrie multiterm_trie;
	SyllableDATrie syllable_trie;

private:
	// An array of HashMaps, represent sparse 2D array of weights
	// Used for retrieving 2-gram weights in sticky-text-segmentation
	std::vector< fast_map_t > nontone_pair_freq_map;

	struct Range
	{
		// int left;
		int right;
		// [left, right), left inclusive, right exclusive
		double weight;
		bool has_more;
		bool is_special;

		Range(int right = -1, double weight = 0.5, bool has_more = false, bool is_special = false)
		{
			this->right = right;
			this->has_more = has_more;
			this->weight = weight;
			this->is_special = is_special;
		}
	};

	struct TemporaryTokenData
	{
		trie_node_t cur_node;
		int last_delimiter_pos;
		bool numeric_prefix;
		bool in_dict;

		TemporaryTokenData(trie_node_t root = 0)
		{
			cur_node = root;
			last_delimiter_pos = -1;
			numeric_prefix = false;
			in_dict = true;
		}
	};

	int unserialize_nontone_data(const std::string &file_name)
	{
		FILE *in = fopen(file_name.c_str(), "rb");
		if (in == nullptr)
		{
			std::cerr << "Error: cannot open " << file_name << " for reading" << std::endl;
			return -1;
		}
		int n = 0;
		if (fread(&n, sizeof(n), 1, in) != 1) {
			fclose(in);
			return -1;
		}
		nontone_pair_freq_map.resize(n);
		FileSerializer serializer;
		for (int i = 0; i < n; ++i)
		{
			nontone_pair_freq_map[i].unserialize(serializer, in);
		}
		fclose(in);
		return 0;
	}

	int load_serialized_dicts(const std::string &dict_path, bool load_nontone_data)
	{
		int status_code = 0;
		if (0 > (status_code = multiterm_trie.read_from_file(dict_path + '/' + MULTITERM_DICT_DUMP)))
			return status_code;
		if (load_nontone_data)
		{
			if (0 > (status_code = syllable_trie.read_from_file(dict_path + '/' + SYLLABLE_DICT_DUMP)))
				return status_code;
			if (0 > (status_code = unserialize_nontone_data(dict_path + '/' + NONTONE_PAIR_DICT_DUMP)))
				return status_code;
		}
		return 0;
	}

public:
	Tokenizer()
	{
	}

	static Tokenizer &instance()
	{
		static Tokenizer tokenizer_object;
		return tokenizer_object;
	}

	int initialize(const std::string &dict_path, bool load_nontone_data = true)
	{
		int status_code = 0;
		if (0 > (status_code = VnLangTool::init(dict_path))) return status_code;
		if (0 > (status_code = load_serialized_dicts(dict_path, load_nontone_data))) return status_code;
		return 0;
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

	/*
	** grab the next token from a starting position in text
	** go as far as possibe while the current term is in dict
	** when ran out of dict, use heuristics to decide what to return
	*/
	Range get_next_token(const uint32_t *text, int length, int from, TemporaryTokenData &state)
	{
		// grab the possible next token from a specific position & state
		trie_node_t &cur_node = state.cur_node;
		int &last_delimiter_pos = state.last_delimiter_pos;
		bool &numeric_prefix = state.numeric_prefix;
		bool &in_dict = state.in_dict;

		for (int i = from; i <= length; ++i)
		{
			if (i != from && !VnLangTool::is_alphanumeric(text[i - 1]))
			{
				last_delimiter_pos = i - 1;
			}
			// If appending the current character don't make the buffer out of dict, then do it
			if (in_dict && i < length && multiterm_trie.has_child(cur_node, text[i]))
			{
				// If the current character is a space, then cut here, there's more to process though
				if (text[i] == ' ' && i != from)
				{
					return Range(i,
						multiterm_trie.get_weight(cur_node),
						true,
						multiterm_trie.is_special(cur_node));
				}

				if (VnLangTool::is_digit(text[i]))
				{
					if (i == from) numeric_prefix = true;
				}
				else
				{
					numeric_prefix = false;
				}

				cur_node = multiterm_trie.get_child(cur_node, text[i]);
			}
			else
			{ // This is when we went out of dict, use heuristics to extract the next token
				in_dict = false;
				if (numeric_prefix)
				{
					// End of text, nothing to proceed
					if (i == length)
						return Range(i,
							multiterm_trie.get_weight(cur_node),
							false,
							multiterm_trie.is_special(cur_node));
					while (i < length && VnLangTool::is_digit(text[i]))
					{
						i++;
					}

					// This is for decimal number cases: "3.1", "99,99"
					while (i + 1 < length && (text[i] == ',' || text[i] == '.') &&
						VnLangTool::is_digit(text[i + 1]))
					{
						i++;
						while (i < length && VnLangTool::is_digit(text[i]))
						{
							i++;
						}
					}

					// long term of mixed NUMBERs and LETTERs,
					if (i < length && VnLangTool::is_alphabetic(text[i]))
					{
						if (i != from) return Range(i, 0.5, true);
						int alphabetic_till = i + 1;
						while (alphabetic_till < length &&
							VnLangTool::is_alphanumeric(text[alphabetic_till]))
						{
							alphabetic_till++;
						}
						return Range(alphabetic_till,
							0.5 + std::max(0, (alphabetic_till - i - 2)) * 0.25);
					}
					return Range(i);
				}

				// End of text OR the current character is non-alphanumeric
				if (i == length || !VnLangTool::is_alphanumeric(text[i]))
				{
					if (i == from) continue;
					// The buffer is a full word OR there's no previous delimiter, then just stop
					// here
					if ((multiterm_trie.is_ending(cur_node)) || last_delimiter_pos == -1)
					{
						return Range(i,
							multiterm_trie.get_weight(cur_node),
							false,
							multiterm_trie.is_special(cur_node));
					}
					else
					{ // There's some previous delimiter, cut there
						return Range(last_delimiter_pos,
							multiterm_trie.get_weight(cur_node),
							false,
							multiterm_trie.is_special(cur_node));
					}
				}
				else
				{
					// current character is alphanumeric
					// if built enough buffer size and current position is transition from
					// text-prefix to number, then stop here
					if (i - from > 2 && multiterm_trie.is_ending(cur_node) &&
						(i == length || (VnLangTool::is_alphabetic(text[i - 1]) &&
									!VnLangTool::is_alphabetic(text[i]))))
					{
						if (multiterm_trie.is_ending(cur_node))
						{
							return Range(i,
								multiterm_trie.get_weight(cur_node),
								false,
								multiterm_trie.is_special(cur_node));
						}
						else
						{
							while (i < length && VnLangTool::is_alphanumeric(text[i]))
							{
								i++;
							}
							return Range(i);
						}
					}

					// If there's no previous delimter, go as far as possible then stop
					if (last_delimiter_pos == -1)
					{
						while (i < length && VnLangTool::is_alphanumeric(text[i]))
						{
							i++;
						}
						return Range(i);
					}
					else
					{ // There's some previous delimter, possibly the last character
						if (text[last_delimiter_pos] != ' ')
						{
							if (multiterm_trie.is_ending(cur_node))
							{
								return Range(i,
									multiterm_trie.get_weight(cur_node),
									false,
									multiterm_trie.is_special(cur_node));
							}
							while (i < length && VnLangTool::is_alphanumeric(text[i]))
							{
								i++;
							}
							return Range(i);
						}
						return Range();
					}
				}
			}
		}
		return Range();
	}

	// function used in JNI
	void normalize_for_tokenization(const unsigned short *original_text,
		int length,
		std::vector< uint32_t > &text,
		std::vector< int > &original_pos,
		bool calc_original_pos)
	{
		// TODO: original_text is encoded in UTF-16, convert to UTF-32
		if (calc_original_pos)
		{
			original_pos.reserve(length + 1);
		}

		for (int i = 0; i < length; ++i)
		{
			uint32_t cur_char = VnLangTool::lower(original_text[i]);
			if (i > 0)
			{
				if (!VnLangTool::merge_tone_hat(text.back(), cur_char))
				{
					if (calc_original_pos)
					{
						original_pos.push_back(i);
					}
					text.push_back(cur_char);
				}
			}
			else
			{
				if (calc_original_pos)
				{
					original_pos.push_back(i);
				}
				text.push_back(cur_char);
			}
		}
		if (calc_original_pos) original_pos.push_back(length);
	}

	// function used in C++ code
	void normalize_for_tokenization(
		const std::string &original_text, std::vector< uint32_t > &text, std::vector< int > &original_pos)
	{
		original_pos.reserve(original_text.length() + 1);
		const char *cur_pointer = &original_text[0];
		const char *end_pointer = cur_pointer + original_text.size();
		while (cur_pointer < end_pointer)
		{
			int cur_position = cur_pointer - &original_text[0];
			int cur_codepoint = VnLangTool::lower(utf8::unchecked::next(cur_pointer));
			if (!text.empty())
			{
				if (!VnLangTool::merge_tone_hat(text.back(), cur_codepoint))
				{
					original_pos.push_back(cur_position);
					text.push_back(cur_codepoint);
				}
			}
			else
			{
				original_pos.push_back(cur_position);
				text.push_back(cur_codepoint);
			}
		}
		original_pos.push_back(original_text.length());
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

	/*
	** core tokenize function
	** receives an array of codepoints
	** generates a vector of tokens
	** used in both C++ and JNI
	** space_positions contains positions which spaces should be inserted (as a result of sticky text segmentation)
	** must be careful to update ranges of tokens
	** another easier way is directly add spaces in normalized_text, and update original_pos accordingly
	** but that requires reallocation of the array
	** although slower and consumes more memory, that way is use in tokenization of urls for simplicity
	*(run_tokenize_url() below)
	*/
	template < class T >
	void run_tokenize(uint32_t *text,
		int length,
		std::vector< T > &ranges,
		std::vector< int > &space_positions,
		bool for_transforming = false,
		bool tokenize_sticky = true,
		bool dont_push_puncts = false)
	{

		std::vector< double > best_scores(length + 1, 0);
		std::vector< int > trace(length + 1, -1);
		std::vector< bool > is_special(length + 1, 0);
		/*
		** run dynamic programming to find the max-weight split
		** best_scores[i] = maximum score of prefix text[0..(i-1)], i.e first i characters
		** trace[i] = -1 when we should skip text[i] (this is considered a PUNCT)
		** trace[i] = start pos of last token otherwise
		** cost of an individual token is pre-calculated in trie
		*/
		double last_score = 0;
		bool should_go = true;
		for (int i = 0; i < length; ++i)
		{
			if (~trace[i])
			{
				last_score = best_scores[i];
				should_go = true;
			}
			if (VnLangTool::is_alphanumeric(text[i]))
			{
				if (!should_go) continue;
				should_go = false;
				TemporaryTokenData state;
				Range token = get_next_token(text, length, i, state);
				while (~token.right)
				{
					if (maximize(best_scores[token.right], last_score + token.weight))
					{
						trace[token.right] = i;
						is_special[token.right] = token.is_special;
					}
					if (token.has_more)
					{
						token = get_next_token(text, length, token.right, state);
					}
					else
					{
						break;
					}
				}
			}
		}

		ranges.reserve(length >> 1);
		bool next_is_domain = false; // for adjusting tokens'seg_type in URLs
		for (int i = length; i > 0;)
		{
			if (~trace[i])
			{
				ranges.push_back(T(trace[i], i));
				ranges.back().type = T::get_type(text, trace[i], i);
				if (!tokenize_sticky)
				{ // this is ok since we only use sticky-segmentation in URLS
					i = trace[i];
					continue;
				}
				if (is_special[i])
				{
					ranges.back().seg_type = T::SKIP_SEG_TYPE;
				}
				else
				{
					// Now deal with special cases

					if (ranges.back().type == T::NUMBER)
					{
						if (ranges.back().normalized_end < length &&
							text[ranges.back().normalized_end] == '%')
						{
							// hack for percentage sign
							ranges.back().normalized_end++;
							ranges.back().type = T::WORD;
							ranges.back().seg_type = T::SKIP_SEG_TYPE;
						}
						else if (ranges.size() > 1)
						{
							T &second_last = ranges[ranges.size() - 2];
							if (second_last.normalized_start ==
									ranges.back().normalized_end &&
								second_last.normalized_end -
										second_last.normalized_start ==
									2 &&
								Helper::is_ordinal_suffix(
									text[second_last.normalized_start],
									text[second_last.normalized_start + 1]))
							{
								// Ordinal number cases: "1st", "10th"
								ranges.back().normalized_end += 2;
								ranges.back().type = T::WORD;
								ranges.back().seg_type = T::SKIP_SEG_TYPE;
								std::swap(ranges.back(), second_last);
								ranges.pop_back();
							}
						}
					}

					// special forms: ([a-z]|\d+)(\^|\+)([a-z]|\d+)
					// Examples: x^y, a+b, 12+13, x+1
					if (ranges.size() > 1)
					{
						T &second_last = ranges[ranges.size() - 2];
						if (Helper::is_special_operator_sign(
							    text[ranges.back().normalized_end]) &&
							ranges.back().normalized_end + 1 ==
								second_last.normalized_start &&
							Helper::is_small_number_or_az_char(text, ranges.back()) &&
							Helper::is_small_number_or_az_char(text, second_last))
						{
							ranges.back().normalized_end = second_last.normalized_end;
							ranges.back().type = T::WORD;
							ranges.back().seg_type = T::SKIP_SEG_TYPE;
							std::swap(ranges.back(), second_last);
							ranges.pop_back();
						}
					}
				}
				if (ranges.back().type == T::NUMBER)
				{
					ranges.back().seg_type = T::SKIP_SEG_TYPE;
				}

				// parse URL based on DOMAIN_ENDs (".com", ".org", etc.)
				if (next_is_domain)
				{
					if (Helper::is_domain_field(
						    text, ranges.back().normalized_start, ranges.back().normalized_end))
					{
						ranges.back().seg_type = T::END_URL_TYPE;
					}
					else
					{
						// found an URL-indicator previously, the current token must be a
						// domain name
						ranges.back().seg_type = T::URL_SEG_TYPE;
						int last_space_pos = Helper::find_last_space_pos(text, ranges.back());
						if (last_space_pos == -1)
						{
							next_is_domain =
								ranges.back().normalized_start > 0 &&
								text[ranges.back().normalized_start - 1] == '.';
						}
						else
						{
							int save_start = ranges.back().normalized_start;
							ranges.back().normalized_start = last_space_pos + 1;
							ranges.push_back(T(save_start, last_space_pos));
							next_is_domain = false;
						}
					}
				}
				else
				{
					// try to check if the current token is in DOMAIN_ENDs
					int left = ranges.back().normalized_start;
					int right = ranges.back().normalized_end;
					if (Helper::is_domain_end(text, left, right))
					{
						// adjust seg_type based on the convention in CompositeTokenizer
						if (Helper::is_domain_field(text, left, right))
						{
							int till = (int) ranges.size() - 2;
							while (till >= 0 &&
								ranges[till].normalized_start ==
									ranges[till + 1].normalized_end + 1 &&
								text[ranges[till].normalized_start - 1] == '.')
							{
								till--;
							}
							++till;
							ranges[till++].seg_type = T::SKIP_SEG_TYPE;
							while (till < (int) ranges.size())
							{
								ranges[till++].seg_type = T::END_URL_TYPE;
							}
						}
						else
						{
							ranges.back().seg_type = T::SKIP_SEG_TYPE;
						}
						next_is_domain = true;
					}
				}

				Token last_token = ranges.back();
				if (last_token.seg_type == T::URL_SEG_TYPE && !nontone_pair_freq_map.empty())
				{
					// sticky tokenization on URL parts
					std::vector< int > sub_space_positions;
					tokenize_pure_sticky_to_syllables(text + last_token.normalized_start,
						last_token.normalized_end - last_token.normalized_start,
						sub_space_positions);
					if (!sub_space_positions.empty())
					{
						std::vector< uint32_t > subtext;
						subtext.reserve(last_token.normalized_end -
								last_token.normalized_start +
								(int) sub_space_positions.size());
						for (int pos = last_token.normalized_start, it = 0;
							pos < last_token.normalized_end;
							++pos)
						{
							if (it < (int) sub_space_positions.size() &&
								pos - last_token.normalized_start ==
									sub_space_positions[it])
							{
								subtext.push_back(' ');
								it++;
							}
							subtext.push_back(text[pos]);
						}

						std::vector< T > subranges;
						// this is hacky, we must ensure that space_positions param
						// passed to run_tokenize cannot be modified
						run_tokenize< T >(subtext.data(),
							subtext.size(),
							subranges,
							sub_space_positions,
							false,
							false);
						ranges.pop_back();
						for (int range_id = (int) subranges.size() - 1,
							 it = (int) sub_space_positions.size() - 1;
							range_id >= 0;
							--range_id)
						{
							ranges.push_back(subranges[range_id]);
							ranges.back().seg_type = last_token.seg_type;
							while (it >= 0 &&
								sub_space_positions[it] + it >=
									ranges.back().normalized_end)
							{
								it--;
							}
							ranges.back().normalized_end += last_token.normalized_start;
							ranges.back().normalized_end -= it + 1;
							while (it >= 0 &&
								sub_space_positions[it] + it >
									ranges.back().normalized_start)
							{
								space_positions.push_back(sub_space_positions[it] +
											  last_token.normalized_start);
								it--;
							}

							ranges.back().normalized_start += last_token.normalized_start;
							ranges.back().normalized_start -= it + 1;
						}
					}
				}

				i = trace[i];
			}
			else
			{
				--i;
			}
		}

		// Now ranges store tokens in reverse order (from end to begin of the text)
		if (for_transforming)
		{
			// push back the tokens and puncts in reverse order
			int sum_length = 0;
			for (T &it : ranges)
			{
				sum_length += it.normalized_end - it.normalized_start;
			}
			std::vector< T > temp;
			temp.swap(ranges);
			ranges.reserve(length - sum_length + temp.size());
			int last_pos = 0;
			bool inside_url = false;
			while (!temp.empty())
			{
				// shouldn't push PUNCTS between URL-parts
				if (!dont_push_puncts &&
					!(inside_url &&
						(temp.back().is_url_related() ||
							(temp.back().seg_type == T::SKIP_SEG_TYPE &&
								text[temp.back().normalized_start - 1] == '.'))))
				{
					while (last_pos < temp.back().normalized_start)
					{
						ranges.push_back({last_pos, last_pos + 1});
						ranges.back().type = text[last_pos] == ' ' ? T::SPACE : T::PUNCT;
						last_pos++;
					}
				}
				ranges.push_back(temp.back());
				// convention from CompositeTokenizer. convert SPACE to UNDERSCORE, convert UNDERSCORE
				// in special_terms to '~'
				for (int i = temp.back().normalized_start; i < temp.back().normalized_end; ++i)
				{
					if (text[i] == '_') text[i] = '~';
					if (text[i] == ' ') text[i] = '_';
				}
				last_pos = temp.back().normalized_end;
				inside_url = temp.back().is_url_related();
				temp.pop_back();
			}
			// PUNCTs at the end of the text
			if (!dont_push_puncts)
			{
				while (last_pos < length)
				{
					ranges.push_back({last_pos, last_pos + 1});
					ranges.back().type = text[last_pos] == ' ' ? T::SPACE : T::PUNCT;
					last_pos++;
				}
			}
		}
		else
		{
			std::reverse(ranges.begin(), ranges.end());
		}
		if (tokenize_sticky)
		{
			std::reverse(space_positions.begin(), space_positions.end());
		}
	}

	/*
	** tokenize sticky alphanumeric text into syllables
	** return a vector of split-positions
	** such positions should have spaces inserted
	** used as a subroutine for more general methods
	*/
	void tokenize_pure_sticky_to_syllables(const uint32_t *text, int length, std::vector< int > &space_positions)
	{
		if (!text || length <= 0) return;

		static const int MAX_TOKEN_LENGTH = 25;

		int space_positions_begin_size = space_positions.size();

		/*
		** dynamic programming with 2-gram
		** best_scores[i][j] = maximum score for text[0..(i-1)], i.e first i characters,
		** with j is the length of the last token (ending at i-1)
		** trace[i][j] = length of the second last token (length of the last token is obviously j, so no need to
		*trace that)
		*/
		std::vector< std::vector< double > > best_scores(length + 1, std::vector< double >());
		std::vector< std::vector< int > > all_token_lengths(length + 1, std::vector< int >());
		std::vector< std::vector< int > > trace(length + 1, std::vector< int >());
		std::vector< std::vector< trie_node_t > > syll_node(length + 1, std::vector< trie_node_t >());

		for (int i = 0; i <= length; ++i)
		{
			best_scores[i].assign(std::min(MAX_TOKEN_LENGTH, i) + 1, -1);
			trace[i].assign(best_scores[i].size(), -1);
			syll_node[i].assign(best_scores[i].size(), -1);
		}

		best_scores[0][0] = 0;
		all_token_lengths[0].push_back(0);
		for (int i = 0; i < length; ++i)
		{
			if (!all_token_lengths[i].empty())
			{
				trie_node_t next_node = 0;
				for (int j = i; j < i + MAX_TOKEN_LENGTH && j < length; ++j)
				{
					if (!syllable_trie.has_child(next_node, text[j])) break;
					syll_node[j + 1][j - i + 1] = next_node =
						syllable_trie.get_child(next_node, text[j]);
				}
			}

			for (int last_token_length : all_token_lengths[i])
			{
				trie_node_t last_node = syll_node[i][last_token_length];
				for (int j = i; j < i + MAX_TOKEN_LENGTH && j < length; ++j)
				{
					int self_len = j - i + 1;
					trie_node_t next_node = syll_node[j + 1][self_len];
					if (next_node == -1) break;

					double cur_score = syllable_trie.get_weight(next_node);
					if ((~last_node) && (~syllable_trie.get_index(last_node)) &&
						(~syllable_trie.get_index(next_node)))
					{
						auto it =
							nontone_pair_freq_map[syllable_trie.get_index(last_node)].find(
								syllable_trie.get_index(next_node));
						if (it !=
							nontone_pair_freq_map[syllable_trie.get_index(last_node)].end())
						{
							cur_score += it->second;
						}
					}

					double total_score = best_scores[i][last_token_length] + cur_score;
					if (best_scores[j + 1][self_len] < total_score)
					{
						best_scores[j + 1][self_len] = total_score;
						trace[j + 1][self_len] = last_token_length;
						if (all_token_lengths[j + 1].empty() ||
							all_token_lengths[j + 1].back() != self_len)
						{
							all_token_lengths[j + 1].push_back(self_len);
						}
					}
				}
			}
		}

		int last_token_length = 0;
		for (int j = 1; j < (int) best_scores[length].size(); ++j)
		{
			if (trace[length][j] >= 0)
			{
				if (best_scores[length][last_token_length] < best_scores[length][j])
				{
					last_token_length = j;
				}
			}
		}
		for (int i = length, j = last_token_length; i > 0;)
		{
			if (trace[i][j] >= 0)
			{
				int new_i = i - j;
				if (new_i)
				{
					if (!(new_i > 0 && VnLangTool::is_digit(text[new_i - 1]) &&
						    VnLangTool::is_digit(text[new_i])))
					{
						space_positions.push_back(new_i);
					}
				}
				j = trace[i][j];
				i = new_i;
			}
			else
			{
				break;
			}
		}

		std::reverse(space_positions.begin() + space_positions_begin_size, space_positions.end());
	}

	void tokenize_sticky_to_syllables(std::vector< uint32_t > &text, std::vector< int > &space_positions)
	{
		auto push_results = [&text, &space_positions, this](int left, int right)
		{
			int start_pos = space_positions.size();
			tokenize_pure_sticky_to_syllables(text.data() + left, right - left, space_positions);
			for (int i = start_pos; i < (int) space_positions.size(); ++i)
			{
				space_positions[i] += left;
			}
		};

		int last_non_alphanumeric = -1;
		for (int i = 0; i < (int) text.size(); ++i)
		{
			if (!VnLangTool::is_alphanumeric(text[i]))
			{
				if (last_non_alphanumeric + 1 != i)
				{
					push_results(last_non_alphanumeric + 1, i);
				}
				last_non_alphanumeric = i;
			}
		}
		if (last_non_alphanumeric + 1 != (int) text.size())
		{
			push_results(last_non_alphanumeric + 1, text.size());
		}
	}

	template < class T >
	void run_tokenize_url(std::vector< uint32_t > &text,
		std::vector< T > &ranges,
		std::vector< int > &space_positions,
		std::vector< int > &original_pos,
		bool for_transforming)
	{

		int start_index = 0;
		if (Helper::vector_match_string(text, "http"))
		{
			if (Helper::vector_match_string(text, "://", 4))
			{
				start_index = 7;
			}
			else if (Helper::vector_match_string(text, "s://", 4))
			{
				start_index = 8;
			}
		}

		std::vector< uint32_t > new_text;
		std::vector< int > new_original_pos;

		auto push = [&text, &new_text, &space_positions, &original_pos, &new_original_pos, this](
			int from, int to)
		{
			int sublength = to - from;
			size_t it = space_positions.size();
			tokenize_pure_sticky_to_syllables(text.data() + from, sublength, space_positions);
			for (int pos = 0; pos < sublength; ++pos)
			{
				if (it < space_positions.size() && pos == space_positions[it])
				{
					space_positions[it] = new_text.size();
					new_text.push_back(' ');
					new_original_pos.push_back(original_pos[from + pos]);
					it++;
				}
				new_text.push_back(text[from + pos]);
				new_original_pos.push_back(original_pos[from + pos]);
			}
		};

		int last_non_alphanumeric = start_index - 1;
		for (int i = start_index; i < (int) text.size(); ++i)
		{
			if (!VnLangTool::is_alphanumeric(text[i]))
			{
				if (last_non_alphanumeric + 1 != i)
				{
					push(last_non_alphanumeric + 1, i);
				}
				if (text[i] != '.' && text[i] != '/')
				{
					new_text.push_back(' ');
				}
				else
				{
					new_text.push_back(text[i]);
				}
				new_original_pos.push_back(original_pos[i]);
				last_non_alphanumeric = i;
			}
		}
		if (last_non_alphanumeric + 1 != (int) text.size())
		{
			push(last_non_alphanumeric + 1, text.size());
		}
		new_original_pos.push_back(original_pos.back());

		text.swap(new_text);
		original_pos.swap(new_original_pos);
		run_tokenize< T >(
			text.data(), text.size(), ranges, space_positions, for_transforming, false, true);
	}

	template < class T >
	void run_tokenize_host(
		std::vector< uint32_t > &text, std::vector< T > &ranges, std::vector< int > &original_pos)
	{
		int new_length = 0;
		int last_dot_position = -1;

		for (int i = 0; i < (int) text.size(); ++i)
		{
			if (VnLangTool::is_alphanumeric(text[i]))
			{
				text[new_length] = text[i];
				original_pos[new_length] = original_pos[i];
				new_length++;
			}
			else if (text[i] == '.')
			{
				ranges.push_back(T(last_dot_position + 1, new_length));
				last_dot_position = new_length;
				text[new_length] = text[i];
				original_pos[new_length] = original_pos[i];
				new_length++;
			}
		}
		original_pos[new_length] = original_pos.back();
		ranges.push_back(T(last_dot_position + 1, new_length));

		text.resize(new_length);
	}

	/*
	** compositor
	** used in both JNI and C++
	*/
	template < class T >
	void handle_tokenization_request(std::vector< uint32_t > &text,
		std::vector< T > &ranges,
		std::vector< int > &space_positions,
		std::vector< int > &original_pos,
		bool for_transforming,
		int tokenize_option)
	{

		if (tokenize_option == TOKENIZE_NORMAL)
		{
			Tokenizer::instance().run_tokenize< T >(
				text.data(), text.size(), ranges, space_positions, for_transforming);
		}
		else if (tokenize_option == TOKENIZE_HOST)
		{
			Tokenizer::instance().run_tokenize_host< T >(text, ranges, original_pos);
		}
		else if (tokenize_option == TOKENIZE_URL)
		{
			Tokenizer::instance().run_tokenize_url< T >(
				text, ranges, space_positions, original_pos, for_transforming);
		}
		else
		{
			std::cerr << "Invalid tokenize_option " << tokenize_option << std::endl;
			return;
		}
	}

	/*
	** wrapper function
	** used in C++ code
	*/
	std::vector< FullToken > segment(
		const std::string &original_text, bool for_transforming = false, int tokenize_option = TOKENIZE_NORMAL)
	{
		std::vector< uint32_t > text;
		std::vector< int > original_pos;
		normalize_for_tokenization(original_text, text, original_pos);

		std::vector< FullToken > res;
		std::vector< int > space_positions;

		handle_tokenization_request< FullToken >(
			text, res, space_positions, original_pos, for_transforming, tokenize_option);

		if (tokenize_option == TOKENIZE_URL) space_positions.clear(); // space_positions is not necessary for normalized text

		space_positions.push_back(-1);
		for (int i = 0, it = 0; i < (int) res.size(); ++i)
		{
			res[i].original_start += original_pos[res[i].normalized_start];
			res[i].original_end += original_pos[res[i].normalized_end];
			res[i].text.reserve(res[i].original_end - res[i].original_start + 1);
			for (int pos = res[i].normalized_start; pos < res[i].normalized_end; ++pos)
			{
				if (space_positions[it] == pos)
				{
					res[i].text += (for_transforming ? '_' : ' ');
					it++;
				}
				utf8::append(text[pos], std::back_inserter(res[i].text));
			}
		}

		return res;
	}

	std::vector< FullToken > segment_original(
		const std::string &original_text, int tokenize_option = TOKENIZE_NORMAL)
	{
		std::vector< uint32_t > text;
		std::vector< int > original_pos;
		normalize_for_tokenization(original_text, text, original_pos);

		std::vector< FullToken > res;
		std::vector< int > space_positions;

		handle_tokenization_request< FullToken >(
			text, res, space_positions, original_pos, false, tokenize_option);

		for (int &pos : space_positions) pos = original_pos[pos];
		space_positions.push_back(-1);

		for (int i = 0, it = 0; i < (int) res.size(); ++i)
		{
			res[i].original_start += original_pos[res[i].normalized_start];
			res[i].original_end += original_pos[res[i].normalized_end];
			res[i].text.reserve(res[i].original_end - res[i].original_start + 1);
			for (int pos = res[i].original_start; pos < res[i].original_end; ++pos)
			{
				if (space_positions[it] == pos)
				{
					if (pos > res[i].original_start) {
						res[i].text += '_';
					}
					it++;
				}
				res[i].text += original_text[pos] == ' ' ? '_' : original_text[pos];
			}
		}

		return res;
	}

	std::string segment_sticky_to_string(const std::string &original_text)
	{
		std::vector< uint32_t > text;
		std::vector< int > original_pos;
		normalize_for_tokenization(original_text, text, original_pos);
		std::vector< int > space_positions;
		Tokenizer::instance().tokenize_sticky_to_syllables(text, space_positions);

		std::string res_str;
		int it = 0;
		for (int i = 0; i < (int) text.size(); ++i)
		{
			if (it < (int) space_positions.size() && space_positions[it] == i)
			{
				res_str += ' ';
				it++;
			}
			if (text[i] < 128)
			{
				res_str += char(text[i]);
			}
			else
			{
				res_str += '?';
			}
		}

		return res_str;
	}

	// warning: this function is slower than segment(), since strings are copied to new vector
	std::vector< std::string > segment_to_string_list(
		const std::string &text, bool for_transforming = false, int tokenize_option = TOKENIZE_NORMAL)
	{
		std::vector< FullToken > full_res = segment(text, for_transforming, tokenize_option);
		std::vector< std::string > res;
		for (auto it : full_res)
		{
			res.push_back(it.text);
		}
		return res;
	}

	// reimplement of segment_original for general purpose (python wrapping)
	std::vector< FullToken > segment_general(const std::string &original_text, int tokenize_option = TOKENIZE_NORMAL) {
		std::vector< uint32_t > text;
		std::vector< int > original_pos;
		normalize_for_tokenization(original_text, text, original_pos);

		std::vector< FullToken > res;
		std::vector< int > space_positions;

		// using for_transforming to keep punctuations
		handle_tokenization_request< FullToken >(
			text, res, space_positions, original_pos, /*for_transforming*/ true, tokenize_option);

		for (int &pos : space_positions) pos = original_pos[pos];
		space_positions.push_back(-1);

		for (int i = 0, it = 0; i < (int) res.size(); ++i)
		{
			res[i].original_start += original_pos[res[i].normalized_start];
			res[i].original_end += original_pos[res[i].normalized_end];
			res[i].text.reserve(res[i].original_end - res[i].original_start + 1);
			for (int pos = res[i].original_start; pos < res[i].original_end; ++pos)
			{
				if (space_positions[it] == pos)
				{
					if (pos > res[i].original_start) {
						res[i].text += '_';
					}
					it++;
				}
				res[i].text += original_text[pos] == ' ' ? '_' : original_text[pos];
			}
		}
		
		// drop empty (space/underscore) tokens
		res.erase(std::remove_if(res.begin(), res.end(), 
								 [&](const FullToken &token) {return (token.text == "_");}),
				  res.end());

		return res;
	}
};

#endif // TOKENIZER_HPP

#include <iostream>
#include <fstream>
#include <stdio.h>
#include <vector>
#include <string>
#include <tokenizer/config.h>
#include "auxiliary/vn_lang_tool.hpp"
#include "auxiliary/trie.hpp"
#include "auxiliary/sparsepp/spp.h"
#include "auxiliary/file_serializer.hpp"
#include "auxiliary/buffered_reader.hpp"

typedef spp::sparse_hash_map< int, float > fast_map_t;

namespace Helper
{
bool is_digit(char c)
{
	return '0' <= c && c <= '9';
}

int find_cut_pos(const std::string &s)
{
	int i = (int) s.size() - 1;
	while (i >= 0 && !is_digit(s[i]))
		--i;
	while (i >= 0 && is_digit(s[i]))
		--i;
	return i;
}

int parse_number(const std::string &s, int from)
{
	int num = 0;
	while (from < (int) s.size() && Helper::is_digit(s[from]))
	{
		num = num * 10 + (s[from] - '0');
		from++;
	}
	return num;
}
}

int load_vndic_multiterm(const std::string &dict_path,
	bool load_nontone_data,
	MultitermHashTrie &multiterm_hashtrie,
	SyllableHashTrie &syllable_hashtrie)
{
	std::ifstream f((dict_path + "/vndic_multiterm").c_str());
	if (!f.is_open())
	{
		std::cerr << "Error openning file, vndic_multiterm" << std::endl;
		return -1;
	}
	std::string line;
	while (getline(f, line))
	{
		int cut_pos = Helper::find_cut_pos(line);
		if (cut_pos == -1) continue;
		int freq = Helper::parse_number(line, cut_pos + 1);
		std::string word = line.substr(0, cut_pos);

		multiterm_hashtrie.add_new_term(word, freq, true, false);
		std::string root_word = VnLangTool::lower_root(word);
		if (word != root_word)
		{
			multiterm_hashtrie.add_new_term(root_word, freq, false, false);
		}
		if (load_nontone_data)
		{
			auto add_syllable = [freq, &syllable_hashtrie](std::string word)
			{
				syllable_hashtrie.add_new_term(word, freq);
				std::string root_word = VnLangTool::lower_root(word);
				if (word != root_word)
				{
					syllable_hashtrie.add_new_term(root_word, freq);
				}
			};

			std::string buffer;
			for (int i = 0; i < (int) word.size(); ++i)
			{
				if (word[i] == ' ')
				{
					add_syllable(buffer);
					buffer = "";
				}
				else
				{
					buffer += word[i];
				}
			}
			if (buffer != "")
			{
				add_syllable(buffer);
			}
		}
	}
	f.close();

	return 0;
}

int load_common_terms(MultitermHashTrie &multiterm_hashtrie)
{
	multiterm_hashtrie.add_new_term("m2", INT_MAX, false, false);
	multiterm_hashtrie.add_new_term("m3", INT_MAX, false, false);
	multiterm_hashtrie.add_new_term("km2", INT_MAX, false, false);
	return 0;
}

int load_and_dump_nontone_pairs(
	const std::string &dict_path, const std::string &out_path, SyllableDATrie &syllable_trie)
{
	// Freq2NontoneUniFile contains all unique nontone syllables
	// Each is given an index
	std::ifstream f((dict_path + "/Freq2NontoneUniFile").c_str());
	if (!f.is_open())
	{
		std::cerr << "Error openning file, Freq2NontoneUniFile" << std::endl;
		return -1;
	}

	std::vector< int > syllable_length;
	std::string s;
	while (f >> s)
	{
		syllable_length.push_back(syllable_trie.update_index(s, syllable_length.size()));
	}
	f.close();

	// This file is HUGE, use custom buffered_reader instead of std::istream
	// nontone_pair_freq contains a description of a sparse 2D-array of frequency
	// freq[i][j] = frequency of pair "syllable[i]-syllable[j]"
	// syllable[i] is the syllable whose index is i, retrieved from Freq2NontoneUniFile
	// nontone_pair_freq is encoded in a way that reduce file size (and potentially improve reading time)
	// It can be compressed in another form (for example gzip) if we need to reduce file size further

	FILE *out_file = fopen(out_path.c_str(), "wb");
	if (out_file == nullptr)
	{
		std::cerr << "Error: cannot open " << out_path << " for writing" << std::endl;
		return -1;
	}
	FileSerializer serializer;

	BufferedReader reader((dict_path + "/nontone_pair_freq").c_str());
	int n = reader.next_int(); // number of rows
	if (n != (int) syllable_length.size())
	{
		std::cerr << "Error: Nontone term count mismatch!" << std::endl;
		return -1;
	}

	fwrite(&n, sizeof(n), 1, out_file);

	for (int first_index = 0; first_index < n; ++first_index)
	{
		fast_map_t cur_map;
		int n_pairs = reader.next_int(); // Start with number of non-zero elements
		int second_index = 0;
		for (int i = 0; i < n_pairs; ++i)
		{
			// Each pair is (diff_from_previous_index, value)
			second_index += reader.next_int();

			int pair_freq = reader.next_int();
			int pair_len = syllable_length[first_index] + syllable_length[second_index];
			static std::vector< float > pair_sticky_params = {
				0.1,      // 0.pair_coeff
				0.994141, // 1.pair_len_power
				0.19,     // 2.pair_power
			};
			float pair_score = pair_sticky_params[0] * std::pow(pair_len, pair_sticky_params[1]) *
					   std::pow(pair_freq, pair_sticky_params[2]);

			cur_map[second_index] = pair_score;
		}
		cur_map.serialize(serializer, out_file);
	}

	fclose(out_file);
	return 0;
}

int load_keywords(const std::string &dict_path, MultitermHashTrie &multiterm_hashtrie)
{
	std::ifstream f((dict_path + "/keywords.freq").c_str());
	if (!f.is_open())
	{
		std::cerr << "Error openning file, keywords.freq" << std::endl;
		return -1;
	}
	std::string line;
	while (getline(f, line))
	{
		int cut_pos = Helper::find_cut_pos(line);
		if (cut_pos == -1) continue;
		int freq = Helper::parse_number(line, cut_pos + 1);
		std::string word = line.substr(0, cut_pos);
		multiterm_hashtrie.add_new_term(word, freq, false, false);
	}
	f.close();

	return 0;
}

int load_acronyms(const std::string &dict_path,
	bool load_nontone_data,
	MultitermHashTrie &multiterm_hashtrie,
	SyllableHashTrie &syllable_hashtrie)
{
	std::ifstream f((dict_path + "/acronyms").c_str());
	if (!f.is_open())
	{
		std::cerr << "Error openning file, acronyms" << std::endl;
		return -1;
	}
	std::string line;
	while (getline(f, line))
	{
		std::istringstream ss(line);
		std::string word;
		int freq;
		ss >> word >> freq;
		multiterm_hashtrie.add_new_term(word, freq, false, false);
		if (load_nontone_data)
		{
			syllable_hashtrie.add_new_term(word, freq);
		}
	}
	f.close();

	return 0;
}

int load_chemical_compounds(const std::string &dict_path, MultitermHashTrie &multiterm_hashtrie)
{
	std::ifstream f((dict_path + "/chemical_comp").c_str());
	if (!f.is_open())
	{
		std::cerr << "Error openning file, chemical_comp" << std::endl;
		return -1;
	}
	std::string line;
	while (f >> line)
	{
		multiterm_hashtrie.add_new_term(line, INT_MAX, false, true);
	}
	f.close();

	return 0;
}

int load_special_terms(const std::string &dict_path, MultitermHashTrie &multiterm_hashtrie)
{
	std::string special_terms[] = {
		"vietnam+", "google+", "notepad++", "c#", "c++", "g++", "xbase++", "vc++", "k+", "g+", "16+", "18+"};
	for (std::string term : special_terms)
	{
		multiterm_hashtrie.add_new_term(term, INT_MAX, false, true);
	}

	std::ifstream f((dict_path + "/special_token.strong").c_str());
	if (!f.is_open())
	{
		std::cerr << "Error openning file, special_token.strong" << std::endl;
		return -1;
	}
	std::string line;
	while (f >> line)
	{
		multiterm_hashtrie.add_new_term(line, INT_MAX, false, true);
	}
	f.close();

	return 0;
}

int load_and_compile_all_dicts(const std::string &dict_path, const std::string &out_path, bool load_nontone_data)
{
	MultitermHashTrie *multiterm_hashtrie = new MultitermHashTrie();
	SyllableHashTrie *syllable_hashtrie = new SyllableHashTrie();
	int status_code = 0;

	if (0 > (status_code = load_vndic_multiterm(
			 dict_path, load_nontone_data, *multiterm_hashtrie, *syllable_hashtrie)))
		return status_code;
	if (0 > (status_code = load_common_terms(*multiterm_hashtrie))) return status_code;
	// load_keywords();
	if (0 > (status_code = load_acronyms(dict_path, load_nontone_data, *multiterm_hashtrie, *syllable_hashtrie)))
		return status_code;
	if (0 > (status_code = load_chemical_compounds(dict_path, *multiterm_hashtrie))) return status_code;
	if (0 > (status_code = load_special_terms(dict_path, *multiterm_hashtrie))) return status_code;

	MultitermDATrie *multiterm_trie = new MultitermDATrie(*multiterm_hashtrie);
	delete multiterm_hashtrie;
	multiterm_trie->dump_to_file(out_path + '/' + MULTITERM_DICT_DUMP);
	delete multiterm_trie;

	SyllableDATrie *syllable_trie = new SyllableDATrie(*syllable_hashtrie);
	delete syllable_hashtrie;

	if (load_nontone_data)
	{
		if (0 > (status_code = load_and_dump_nontone_pairs(
				 dict_path, out_path + '/' + NONTONE_PAIR_DICT_DUMP, *syllable_trie)))
			return status_code;
	}

	syllable_trie->dump_to_file(out_path + '/' + SYLLABLE_DICT_DUMP);
	delete syllable_trie;

	return 0;
}

int main(int argc, char **argv)
{
	if (argc < 3)
	{
		fprintf(stderr,
			"Usage:\n"
			"    %s {INPUT_DICTS_PATH} {OUTPUT_DICTS_PATH}\n"
			"\n",
			argv[0]);
		return -1;
	}
	if (0 > VnLangTool::init(argv[1] + std::string("/vn_lang_tool"))) return -1;
	return load_and_compile_all_dicts(argv[1] + std::string("/tokenizer"), argv[2], true);
}

#ifndef VN_LANG_TOOL_HPP
#define VN_LANG_TOOL_HPP

#include <vector>
#include <iostream>
#include <fstream>
#include <sstream>
#include <bitset>
#include <string>
#include <memory>
#include <cstring>
#include <unordered_map>
#include <chrono>
#include <atomic>

#include <tokenizer/config.h>
#include "utf8.h"

#define VN_LANG_TOOL_INIT_OK 0
#define VN_LANG_TOOL_DICT_NOT_FOUND -1

/*
** Port of VnLangTool.java in C++
*/

namespace VnLangTool
{
// This constant is quite huge for practical uses, but useful if only process inside-BMP codepoints
// If need to optimize memory, one should try to reduce this, then add bound-checks to the functions belows
const int ALPHANUMERIC_SIZE = 1 << 16;

std::string VN_LOWER_CHARSET = "áàảãạâấầẩẫậăắằẳẵặéèẻẽẹêếềểễệíìỉĩịóòỏõọôốồổỗộơớờởỡợúùủũụưứừửữựýỳỷỹỵđđ";
std::string VN_UPPER_CHARSET = "ÁÀẢÃẠÂẤẦẨẪẬĂẮẰẲẴẶÉÈẺẼẸÊẾỀỂỄỆÍÌỈĨỊÓÒỎÕỌÔỐỒỔỖỘƠỚỜỞỠỢÚÙỦŨỤƯỨỪỬỮỰÝỲỶỸỴĐÐ";
std::string root_forms[14] = {"aáàảãạâấầẩẫậăắằẳẵặ",
	"eéèẻẽẹêếềểễệ",
	"iíìỉĩị",
	"oóòỏõọôốồổỗộơớờởỡợ",
	"uúùủũụưứừửữự",
	"yýỳỷỹỵ",
	"dđđ",
	"AÁÀẢÃẠÂẤẦẨẪẬĂẮẰẲẴẶ",
	"EÉÈẺẼẸÊẾỀỂỄỆ",
	"IÍÌỈĨỊ",
	"OÓÒỎÕỌÔỐỒỔỖỘƠỚỜỞỠỢ",
	"UÚÙỦŨỤƯỨỪỬỮỰ",
	"YÝỲỶỸỴ",
	"DĐÐ"};

std::string tone_forms[24] = {"aáàảãạ",
	"âấầẩẫậ",
	"ăắằẳẵặ",
	"eéèẻẽẹ",
	"êếềểễệ",
	"iíìỉĩị",
	"oóòỏõọ",
	"ôốồổỗộ",
	"ơớờởỡợ",
	"uúùủũụ",
	"ưứừửữự",
	"yýỳỷỹỵ",
	"AÁÀẢÃẠ",
	"ÂẤẦẨẪẬ",
	"ĂẮẰẲẴẶ",
	"EÉÈẺẼẸ",
	"ÊẾỀỂỄỆ",
	"IÍÌỈĨỊ",
	"OÓÒỎÕỌ",
	"ÔỐỒỔỖỘ",
	"ƠỚỜỞỠỢ",
	"UÚÙỦŨỤ",
	"ƯỨỪỬỮỰ",
	"YÝỲỶỸỴ"};
std::vector< uint32_t > tone_forms_UTF[24];

std::string hat_forms[24] = {
	"aâăa",
	"áấắá",
	"àầằà",
	"ảẩẳả",
	"ãẫẵã",
	"ạậặạ",
	"eêee",
	"éếéé",
	"èềèè",
	"ẻểẻẻ",
	"ẽễẽẽ",
	"ẹệẹẹ",
	"oôoơ",
	"óốóớ",
	"òồòờ",
	"ỏổỏở",
	"õỗõỡ",
	"ọộọợ",
	"uuuư",
	"úúúứ",
	"ùùùừ",
	"ủủủử",
	"ũũũữ",
	"ụụụự",
};
std::vector< uint32_t > hat_forms_UTF[24];

int tone_forms_id[ALPHANUMERIC_SIZE];
int hat_forms_id[ALPHANUMERIC_SIZE];
int tone_id[ALPHANUMERIC_SIZE];
int hat_id[ALPHANUMERIC_SIZE];

uint32_t lower_of[ALPHANUMERIC_SIZE];
uint32_t upper_of[ALPHANUMERIC_SIZE];
uint32_t root_of[ALPHANUMERIC_SIZE];
uint32_t lower_root_of[ALPHANUMERIC_SIZE];

/*
** When used as a bool array, bitset provides no speed improvements
** But it consumes less memory
*/
// bool in_alphabet[ALPHANUMERIC_SIZE];
// bool in_numeric[ALPHANUMERIC_SIZE];
// bool in_alphanumeric[ALPHANUMERIC_SIZE];
std::bitset< ALPHANUMERIC_SIZE > in_alphabet;
std::bitset< ALPHANUMERIC_SIZE > in_numeric;
std::bitset< ALPHANUMERIC_SIZE > in_alphanumeric;

std::unordered_map< std::string, std::string > transformation;

std::string get_transformation(const std::string &s)
{
	if (transformation.count(s)) return transformation[s];
	return s;
}

std::string get_transformation_string(const std::string &s)
{
	std::string res;
	int last_space = -1;
	for (int i = 0; i < (int) s.size(); ++i)
		if (s[i] == ' ')
		{
			res += get_transformation(s.substr(last_space + 1, i - last_space - 1)) + " ";
			last_space = i;
		}
	res += get_transformation(s.substr(last_space + 1, s.size() - last_space - 1));
	return res;
}

inline uint32_t lower(uint32_t c)
{
	return c < ALPHANUMERIC_SIZE ? lower_of[c] : c;
}

inline uint32_t lower_root(uint32_t c)
{
	return c < ALPHANUMERIC_SIZE ? lower_root_of[c] : c;
}

/*
** Normalize strings which uses Unicode control characters to add tones & hats
** Merge such characters to their previous vowels
*/
std::vector< uint32_t > normalize_NFD_UTF(const std::vector< uint32_t > &text, bool remove_duplicate_spaces = false)
{
	if (text.empty()) return std::vector< uint32_t >();
	std::vector< uint32_t > res;
	res.reserve(text.size());
	res.push_back(text[0]);
	for (int i = 1; i < (int) text.size(); ++i)
	{
		uint32_t &prev_char = res.back();
		uint32_t cur_char = text[i];
		bool changed = false;
		if (prev_char < ALPHANUMERIC_SIZE && (~tone_id[prev_char]) && cur_char < ALPHANUMERIC_SIZE &&
			(~tone_forms_id[cur_char]))
		{
			prev_char = tone_forms_UTF[tone_id[prev_char]][tone_forms_id[cur_char]];
			changed = true;
		}
		if (prev_char < ALPHANUMERIC_SIZE && (~hat_id[prev_char]) && cur_char < ALPHANUMERIC_SIZE &&
			(~hat_forms_id[cur_char]))
		{
			prev_char = hat_forms_UTF[hat_id[prev_char]][hat_forms_id[cur_char]];
			changed = true;
		}
		if (!changed)
		{
			if (remove_duplicate_spaces && !res.empty() && res.back() == ' ' && cur_char == ' ') continue;
			res.push_back(cur_char);
		}
	}
	return res;
}

inline bool can_put_tone_hat(uint32_t c)
{
	return c < ALPHANUMERIC_SIZE && ((~tone_id[c]) || (~hat_id[c]));
}

bool is_tone_hat(uint32_t c)
{
	return c < ALPHANUMERIC_SIZE && ((~tone_forms_id[c]) || (~hat_forms_id[c]));
}

bool merge_tone_hat(uint32_t &prev_char, uint32_t cur_char)
{
	if (prev_char >= ALPHANUMERIC_SIZE || cur_char >= ALPHANUMERIC_SIZE) return false;

	if ((~tone_id[prev_char]) && (~tone_forms_id[cur_char]))
	{
		prev_char = tone_forms_UTF[tone_id[prev_char]][tone_forms_id[cur_char]];
		return true;
	}
	if ((~hat_id[prev_char]) && (~hat_forms_id[cur_char]))
	{
		prev_char = hat_forms_UTF[hat_id[prev_char]][hat_forms_id[cur_char]];
		return true;
	}
	return false;
}

// Convert std::string to vector of codepoints
std::vector< uint32_t > to_lower_UTF(const std::string &text)
{
	std::vector< uint32_t > codepoints;
	utf8::unchecked::iterator< std::string::const_iterator > it(text.begin()), end_it(text.end());
	while (it != end_it)
	{
		codepoints.push_back(lower(*it));
		it++;
	}
	return codepoints;
}

void to_lower_UTF(const std::string &text, std::vector< uint32_t > &buffer_out)
{
	buffer_out.clear();
	utf8::unchecked::iterator< std::string::const_iterator > it(text.begin()), end_it(text.end());
	while (it != end_it)
	{
		buffer_out.push_back(lower(*it));
		it++;
	}
}

std::vector< uint32_t > to_UTF(const std::string &text)
{
	std::vector< uint32_t > codepoints;
	utf8::unchecked::iterator< std::string::const_iterator > it(text.begin()), end_it(text.end());
	while (it != end_it)
	{
		codepoints.push_back(*it);
		it++;
	}
	return codepoints;
}

// Three frequently used functions, can remove bound-check if the codepoint is small for sure
inline bool is_alphabetic(uint32_t c)
{
	return c < ALPHANUMERIC_SIZE ? in_alphabet[c] : false;
}

inline bool is_digit(uint32_t c)
{
	return '0' <= c && c <= '9';
	// return c < ALPHANUMERIC_SIZE ? in_numeric[c] : false;
}

inline bool is_alphanumeric(uint32_t c)
{
	return c < ALPHANUMERIC_SIZE ? in_alphanumeric[c] : false;
}

inline bool is_valid(const std::string &text)
{
	return utf8::is_valid(text.begin(), text.end());
}

std::string lower_char(uint32_t ch)
{
	std::string res;
	if (ch < ALPHANUMERIC_SIZE)
	{
		utf8::append(lower_of[ch], std::back_inserter(res));
	}
	else
	{
		utf8::append(ch, std::back_inserter(res));
	}
	return res;
}

std::string lower_root_char(uint32_t ch)
{
	std::string res;
	if (ch < ALPHANUMERIC_SIZE)
	{
		utf8::append(lower_root_of[ch], std::back_inserter(res));
	}
	else
	{
		utf8::append(ch, std::back_inserter(res));
	}
	return res;
}

void append_lower(std::string &s, uint32_t ch)
{
	if (ch < ALPHANUMERIC_SIZE)
	{
		utf8::append(lower_of[ch], std::back_inserter(s));
	}
	else
	{
		utf8::append(ch, std::back_inserter(s));
	}
}

void append_lower_root(std::string &s, uint32_t ch)
{
	if (ch < ALPHANUMERIC_SIZE)
	{
		utf8::append(lower_root_of[ch], std::back_inserter(s));
	}
	else
	{
		utf8::append(ch, std::back_inserter(s));
	}
}

std::string lower(const std::string &s)
{
	utf8::unchecked::iterator< std::string::const_iterator > it(s.begin()), end_it(s.end());
	std::string res;
	while (it != end_it)
	{
		append_lower(res, *it);
		it++;
	}
	return res;
}

std::vector< uint32_t > map(const std::vector< uint32_t > &text, uint32_t to[ALPHANUMERIC_SIZE])
{
	std::vector< uint32_t > res;
	res.reserve(text.size());
	for (uint32_t c : text)
	{
		res.push_back(c < ALPHANUMERIC_SIZE ? to[c] : c);
	}
	return res;
}

std::vector< uint32_t > lower(const std::vector< uint32_t > &text)
{
	return map(text, lower_of);
}

std::vector< uint32_t > root(const std::vector< uint32_t > &text)
{
	return map(text, root_of);
}

std::vector< uint32_t > upper(const std::vector< uint32_t > &text)
{
	return map(text, upper_of);
}

std::vector< uint32_t > lower_root(const std::vector< uint32_t > &text)
{
	return map(text, lower_root_of);
}

std::string lower_root(const std::string &s)
{
	utf8::unchecked::iterator< std::string::const_iterator > it(s.begin()), end_it(s.end());
	std::string res;
	while (it != end_it)
	{
		append_lower_root(res, *it);
		it++;
	}
	return res;
}

bool make_lower_root(std::vector< uint32_t > &buffer_out)
{
	// return true if text is toned
	bool is_toned = false;
	for (auto &v : buffer_out)
	{
		if (v != lower_root(v))
		{
			is_toned = true;
		}
		v = lower_root(v);
	}
	return is_toned;
}

std::string vector_to_string(const std::vector< uint32_t > &a)
{
	std::string res;
	for (uint32_t it : a)
	{
		utf8::append(it, std::back_inserter(res));
	}
	return res;
}

std::string vector_to_string(const std::vector< uint32_t > &a, int begin_pos, int end_pos)
{
	std::string res;
	for (int i = begin_pos; i < end_pos; ++i)
	{
		utf8::append(a[i], std::back_inserter(res));
	}
	return res;
}

int init_alphanumeric(const std::string &dict_path)
{
	std::ifstream fin;
	int n;

	fin.open((dict_path + "/alphabetic").c_str());
	if (!fin.is_open())
	{
		std::cerr << "Error openning file, alphabetic" << std::endl;
		return VN_LANG_TOOL_DICT_NOT_FOUND;
	}
	fin >> n;
	while (n--)
	{
		std::string upper_str, lower_str;
		uint32_t upper_codepoint, lower_codepoint;
		std::string line;
		getline(fin, line);
		std::istringstream ss(line);
		if (ss >> upper_str >> upper_codepoint >> lower_str >> lower_codepoint)
		{
			if (std::max(upper_codepoint, lower_codepoint) >= ALPHANUMERIC_SIZE) continue;
			in_alphabet[upper_codepoint] = true;
			in_alphanumeric[upper_codepoint] = true;
			in_alphabet[lower_codepoint] = true;
			in_alphanumeric[lower_codepoint] = true;
			if (upper_codepoint != lower_codepoint)
			{
				upper_of[lower_codepoint] = upper_codepoint;
				lower_of[upper_codepoint] = lower_codepoint;
			}
		}
	}
	fin.close();

	fin.open((dict_path + "/numeric").c_str());
	if (!fin.is_open())
	{
		std::cerr << "Error openning file, numeric" << std::endl;
		return VN_LANG_TOOL_DICT_NOT_FOUND;
	}
	fin >> n;
	while (n--)
	{
		std::string upper_str, lower_str;
		uint32_t upper_codepoint, lower_codepoint;
		fin >> upper_str >> upper_codepoint >> lower_str >> lower_codepoint;
		if (std::max(upper_codepoint, lower_codepoint) >= ALPHANUMERIC_SIZE) continue;
		in_numeric[upper_codepoint] = true;
		in_alphanumeric[upper_codepoint] = true;
		in_numeric[lower_codepoint] = true;
		in_alphanumeric[lower_codepoint] = true;
		if (upper_codepoint != lower_codepoint)
		{
			upper_of[lower_codepoint] = upper_codepoint;
			lower_of[upper_codepoint] = lower_codepoint;
		}
	}
	fin.close();

	return VN_LANG_TOOL_INIT_OK;
}

void init_simple_alphanumeric()
{
	for (int i = 0; i <= 9; ++i)
	{
		in_numeric[i + '0'] = true;
	}
	for (int i = 0; i < 26; ++i)
	{
		in_alphabet['A' + i] = true;
		in_alphabet['a' + i] = true;
	}
	utf8::unchecked::iterator< std::string::iterator > lower_it(VN_LOWER_CHARSET.begin());
	utf8::unchecked::iterator< std::string::iterator > lower_end(VN_LOWER_CHARSET.end());
	utf8::unchecked::iterator< std::string::iterator > upper_it(VN_UPPER_CHARSET.begin());
	utf8::unchecked::iterator< std::string::iterator > upper_end(VN_UPPER_CHARSET.end());
	while (lower_it != lower_end)
	{
		in_alphabet[*lower_it] = true;
		in_alphabet[*upper_it] = true;
		lower_it++;
		upper_it++;
	}
}

void init_lower_upper()
{
	for (int i = 0; i < ALPHANUMERIC_SIZE; ++i)
		lower_of[i] = upper_of[i] = i;
	for (int i = 0; i < 26; ++i)
	{
		lower_of['A' + i] = 'a' + i;
		upper_of['a' + i] = 'A' + i;
	}
	utf8::unchecked::iterator< std::string::iterator > lower_it(VN_LOWER_CHARSET.begin());
	utf8::unchecked::iterator< std::string::iterator > lower_end(VN_LOWER_CHARSET.end());
	utf8::unchecked::iterator< std::string::iterator > upper_it(VN_UPPER_CHARSET.begin());
	utf8::unchecked::iterator< std::string::iterator > upper_end(VN_UPPER_CHARSET.end());
	while (lower_it != lower_end)
	{
		lower_of[*upper_it] = *lower_it;
		upper_of[*lower_it] = *upper_it;
		lower_it++;
		upper_it++;
	}
}

void init_root_forms()
{
	for (int i = 0; i < ALPHANUMERIC_SIZE; ++i)
	{
		root_of[i] = i;
		lower_root_of[i] = lower_of[i];
	}
	for (int i = 0; i < 14; ++i)
	{
		utf8::unchecked::iterator< std::string::iterator > it(root_forms[i].begin()),
			end_it(root_forms[i].end());
		while (it != end_it)
		{
			root_of[*it] = root_forms[i][0];
			lower_root_of[*it] = lower_of[root_of[*it]];
			it++;
		}
	}
}

void init_tone_forms()
{
	memset(tone_forms_id, -1, sizeof tone_forms_id);
	memset(tone_id, -1, sizeof tone_id);
	for (int i = 0; i < 24; ++i)
	{
		utf8::unchecked::iterator< std::string::iterator > it(tone_forms[i].begin()),
			end_it(tone_forms[i].end());
		tone_id[*it] = i;
		while (it != end_it)
		{
			tone_forms_UTF[i].push_back(*it);
			it++;
		}
	}
	tone_forms_id[0x301] = 1; // SAC
	tone_forms_id[0x300] = 2; // HUYEN
	tone_forms_id[0x309] = 3; // HOI
	tone_forms_id[0x303] = 4; // NGA
	tone_forms_id[0x323] = 5; // NANG
}

void init_hat_forms()
{
	memset(hat_forms_id, -1, sizeof hat_forms_id);
	memset(hat_id, -1, sizeof hat_id);
	for (int i = 0; i < 24; ++i)
	{
		utf8::unchecked::iterator< std::string::iterator > it(hat_forms[i].begin()), end_it(hat_forms[i].end());
		hat_id[*it] = i;
		while (it != end_it)
		{
			hat_forms_UTF[i].push_back(*it);
			it++;
		}
	}
	hat_forms_id[0x302] = 1; // ê
	hat_forms_id[0x306] = 2; // ă
	hat_forms_id[0x31b] = 3; // ơ
}

int init_transformer(const std::string &dict_path)
{
	std::ifstream fin;
	std::string line;

	fin.open((dict_path + "/d_and_gi.txt").c_str());
	if (!fin.is_open())
	{
		std::cerr << "Error openning file, d_and_gi.txt" << std::endl;
		return VN_LANG_TOOL_DICT_NOT_FOUND;
	}
	while (getline(fin, line))
	{
		std::istringstream ss(line);
		std::string from, to;
		ss >> from >> to;
		transformation[lower(from)] = lower(to);
	}
	fin.close();

	fin.open((dict_path + "/i_and_y.txt").c_str());
	if (!fin.is_open())
	{
		std::cerr << "Error openning file, i_and_y.txt" << std::endl;
		return VN_LANG_TOOL_DICT_NOT_FOUND;
	}
	while (getline(fin, line))
	{
		std::istringstream ss(line);
		std::string from, to;
		ss >> from >> to;
		transformation[lower(from)] = lower(to);
	}
	fin.close();

	return VN_LANG_TOOL_INIT_OK;
}

int init(const std::string &dict_path, bool simple_mode = false)
{
	static std::atomic_flag once_flag;
	if (once_flag.test_and_set()) return VN_LANG_TOOL_INIT_OK;

	if (!simple_mode)
	{
		if (0 > init_alphanumeric(dict_path)) return VN_LANG_TOOL_DICT_NOT_FOUND;
		if (0 > init_transformer(dict_path)) return VN_LANG_TOOL_DICT_NOT_FOUND;
	}
	else
	{
		init_simple_alphanumeric();
	}
	init_lower_upper();
	init_root_forms();
	init_tone_forms();
	init_hat_forms();

	return VN_LANG_TOOL_INIT_OK;
}
}

#endif // VN_LANG_TOOL_HPP

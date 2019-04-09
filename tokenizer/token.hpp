#ifndef TOKENIZER_TOKEN_HPP
#define TOKENIZER_TOKEN_HPP

#include <sstream>
#include "auxiliary/vn_lang_tool.hpp"

// Used in JNI
// In Java we build the text field later, so don't need it here
struct Token
{
	static const uint32_t WORD = 0;
	static const uint32_t NUMBER = 1;
	static const uint32_t SPACE = 2;
	static const uint32_t PUNCT = 3;

	static const uint32_t OTHER_SEG_TYPE = 0;
	static const uint32_t SKIP_SEG_TYPE = 1;
	static const uint32_t URL_SEG_TYPE = 2;
	static const uint32_t END_URL_TYPE = 3;
	static const uint32_t END_SEG_TYPE = 4;

	static uint32_t get_type(const uint32_t *text, int from, int to)
	{
		if (text[from] == ' ')
		{
			return SPACE;
		}
		if (!VnLangTool::is_alphanumeric(text[from]))
		{
			return PUNCT;
		}
		int dot_count = 0;
		for (int i = from; i < to; ++i)
		{
			if (!VnLangTool::is_digit(text[i]))
			{
				if (text[i] == '.' || text[i] == ',')
				{
					dot_count++;
					if (dot_count > 1)
					{
						return WORD;
					}
				}
				else
				{
					return WORD;
				}
			}
		}
		return NUMBER;
	}

	// Everything is declared as int32_t for simplicity
	// So that alignment of these field is simple, useful when we want to grab data from Java
	int32_t normalized_start;
	int32_t normalized_end;
	int32_t original_start;
	int32_t original_end;
	int32_t type;
	int32_t seg_type;

	Token()
	{
	}

	Token(int32_t normalized_start, int32_t normalized_end)
	    : normalized_start(normalized_start),
	      normalized_end(normalized_end),
	      original_start(0),
	      original_end(0),
	      type(0),
	      seg_type(0)
	{
	}

	inline bool is_url_related()
	{
		return seg_type == URL_SEG_TYPE || seg_type == END_URL_TYPE;
	}

	inline int32_t length()
	{
		return normalized_end - normalized_start;
	}
};

// This type is used in C++ code
// We need the text field here
struct FullToken : Token
{
	std::string text;

	FullToken(int32_t normalized_start, int32_t normalized_end) : Token(normalized_start, normalized_end)
	{
	}
	std::string to_string()
	{
		std::ostringstream ss;
		if (type == 0)
		{
			ss << "WORD ";
		}
		else if (type == 1)
		{
			ss << "NUMBER ";
		}
		else if (type == 2)
		{
			ss << "SPACE ";
		}
		else if (type == 3)
		{
			ss << "PUNCT ";
		}
		else
		{
			ss << "UNKNOWN ";
		}

		ss << text << ' ';
		if (seg_type == 0)
		{
			ss << "OTHER ";
		}
		else if (seg_type == 1)
		{
			ss << "SKIP ";
		}
		else if (seg_type == 2)
		{
			ss << "URL ";
		}
		else if (seg_type == 3)
		{
			ss << "END ";
		}
		else if (seg_type == 4)
		{
			ss << "END_SEG ";
		}
		else
		{
			ss << "UNKNOWN ";
		}

		ss << "[" << original_start << "-" << original_end << "]";
		ss << "{" << normalized_start << "-" << normalized_end << "}";
		return ss.str();
	}
};

#endif // TOKENIZER_TOKEN_HPP

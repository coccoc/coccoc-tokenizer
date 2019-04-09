#ifndef BUFFERED_READER_H
#define BUFFERED_READER_H

#include <stdio.h>

/*
** A very simple buffered input reader
** No error handling mechanism
** Must be sure that the file is properly formatted
*/

struct BufferedReader
{
	static const int BUFFER_SIZE = 1 << 13; // enough in practice
	FILE *file;

	BufferedReader(const char *file_name)
	{
		file = fopen(file_name, "rb");
		if (!file)
		{
			std::cerr << "BufferedReader - Cannot open file " << file_name << std::endl;
		}
	}

	~BufferedReader()
	{
		if (file)
		{
			fclose(file);
		}
	}

	inline bool read_byte(uint8_t &res)
	{
		static uint8_t buffer[BUFFER_SIZE];
		static int len = 0, pos = 0;
		if (pos == len) pos = 0, len = fread(buffer, sizeof(uint8_t), BUFFER_SIZE, file);
		if (pos == len) return false;
		res = buffer[pos++];
		return true;
	}

	inline int next_int()
	{
		// little-endian encoding: {first_byte = 0xxxxxx}[later_bytes = 1xxxxxxx]
		static uint8_t last_byte_read = 0xFF;
		int res = 0;
		int power = 0;
		if (last_byte_read != 0xFF)
		{
			res = last_byte_read;
			power = 7;
		}
		uint8_t d = 0;
		while (read_byte(d))
		{
			if (power > 0 && !(d & 0x80))
			{
				last_byte_read = d;
				break;
			}
			res = ((d & 0x7F) << power) | res;
			power += 7;
		}
		return res;
	}
};

#endif // BUFFERED_READER_H

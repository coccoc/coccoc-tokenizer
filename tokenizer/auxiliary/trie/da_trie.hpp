#ifndef DA_TRIE_HPP
#define DA_TRIE_HPP

#include <vector>
#include <set>
#include <string>
#include "../tsl/robin_set.h"

template < class HashNode, class Node >
struct DATrie
{
	template < typename T >
	using fast_hash_set_t = tsl::robin_set< T >;

	std::vector< Node > pool;
	std::vector< int > char_map;
	int alphabet_size;

	DATrie()
	{
	}

	DATrie(const std::string &file_path)
	{
		read_from_file(file_path);
	}

	DATrie(HashTrie< HashNode > &hash_trie)
	{
		build_from_hash_trie(hash_trie);
	}

	void build_from_hash_trie(HashTrie< HashNode > &hash_trie)
	{
		build_all(hash_trie.dump_structure(), hash_trie.dump_alphabet());
	}

	void build_all(const std::vector< HashNode > &trie_info, const std::set< uint32_t > &char_set)
	{
		build_char_map(char_set);
		build_trie(trie_info);
	}

	void build_char_map(const std::set< uint32_t > &char_set)
	{
		this->alphabet_size = char_set.size();
		this->char_map.assign(*char_set.rbegin() + 1, -1);
		int index = 0;
		for (uint32_t c : char_set)
		{
			char_map[c] = index++;
		}
	}

	void build_trie(const std::vector< HashNode > &trie_info)
	{
		std::vector< int > positions = construct(trie_info);
		int last_position = *std::max_element(positions.begin(), positions.end());
		pool.assign(last_position + alphabet_size, Node());

		pool[0].base = 1;
		pool[0].assign_data(trie_info[0]);

		std::vector< int > mapping(trie_info.size());
		for (int i = 0; i < (int) trie_info.size(); ++i)
		{
			for (auto it : trie_info[i].children)
			{
				int index = pool[mapping[i]].base + char_map[it.first];
				mapping[it.second] = index;
				pool[index].base = positions[it.second];
				pool[index].parent = mapping[i];
				pool[index].assign_data(trie_info[it.second]);
			}
		}
	}

	std::vector< int > construct(const std::vector< HashNode > &trie_info)
	{
		auto extract_mask = [this](const HashNode &node) -> std::vector< int >
		{
			std::vector< int > res;
			res.reserve(node.children.size());
			for (auto it : node.children)
			{
				res.push_back(char_map[it.first]);
			}
			return res;
		};

		// pos[i] = set of positions having ith-bit OFF
		std::vector< fast_hash_set_t< int > > pos(alphabet_size);

		std::vector< int > res(trie_info.size());
		std::vector< bool > state;
		state.assign(alphabet_size + 2, 0);
		int cur_end = 2;
		for (int i = 0; i < alphabet_size; ++i)
		{
			pos[i].insert(1);
		}

		auto grab_status_reverse = [&state, this](int from)
		{
			std::vector< int > off_bit_positions;
			for (int i = alphabet_size - 1; i >= 0; --i)
			{
				if (state[from + i] == 0)
				{
					off_bit_positions.push_back(i);
				}
			}
			return off_bit_positions;
		};

		for (int i = 0; i < (int) trie_info.size(); ++i)
		{
			if (trie_info[i].children.empty())
			{
				res[i] = 0;
				continue;
			}

			std::vector< int > mask = extract_mask(trie_info[i]);
			sort(mask.begin(),
				mask.end(),
				[&pos](const int x, const int y)
				{
					return pos[x].size() < pos[y].size();
				});

			int found_pos = -1;
			for (int p : pos[mask[0]])
			{
				bool good = true;
				for (int j = 1; j < (int) mask.size(); ++j)
				{
					if (pos[mask[j]].find(p) == pos[mask[j]].end())
					{
						good = false;
						break;
					}
				}
				if (good)
				{
					found_pos = p; // Yay!
					break;
				}
			}

			if (found_pos == -1)
			{
				found_pos = cur_end;
			}

			res[i] = found_pos;

			int affected_end = found_pos + (*std::max_element(mask.begin(), mask.end()));
			if (cur_end <= affected_end)
			{
				std::vector< int > off_bit_positions = grab_status_reverse(cur_end);
				while (cur_end <= affected_end)
				{
					if (!off_bit_positions.empty() && off_bit_positions.back() < cur_end)
						off_bit_positions.pop_back();
					for (int off_bit_pos : off_bit_positions)
					{
						pos[off_bit_pos].insert(cur_end);
					}
					cur_end++;
					state.push_back(0);
				}
			}

			for (int offset : mask)
			{
				int cur_pos = found_pos + offset;
				for (int affected_pos = cur_pos;
					affected_pos > cur_pos - alphabet_size && affected_pos >= 0;
					--affected_pos)
				{
					pos[cur_pos - affected_pos].erase(affected_pos);
				}
				state[cur_pos] = 1;
			}
		}

		return res;
	}

	inline int get_child(const int u, const uint32_t c)
	{
		return pool[u].base + char_map[c];
	}

	int cache_node;
	inline bool has_child(const int u, const uint32_t c)
	{
		return c < char_map.size() && (~char_map[c]) && pool[cache_node = get_child(u, c)].parent == u;
	}

	int dump_to_file(const std::string &file_path)
	{
		FILE *out_file = fopen(file_path.c_str(), "wb");
		if (out_file == nullptr)
		{
			std::cerr << "Cannot open file for writing " << file_path << std::endl;
			return -1;
		}

		fwrite(&alphabet_size, sizeof(alphabet_size), 1, out_file);
		for (uint32_t i = 0; i < char_map.size(); ++i)
		{
			if (~char_map[i])
			{
				fwrite(&i, sizeof(i), 1, out_file);
			}
		}

		size_t pool_size = pool.size();
		fwrite(&pool_size, sizeof(pool_size), 1, out_file);
		fwrite(pool.data(), sizeof(Node), pool_size, out_file);

		fclose(out_file);
		return 0;
	}

	int read_from_file(const std::string &file_path)
	{
		FILE *in_file = fopen(file_path.c_str(), "rb");
		if (in_file == nullptr)
		{
			std::cerr << "Cannot open file for reading " << file_path << std::endl;
			return -1;
		}

#define RETURN_ERROR \
	{\
		fclose(in_file);\
		std::cerr << "Cannot read full trie information!" << std::endl;\
		return -1;\
	}

		if (fread(&alphabet_size, sizeof(alphabet_size), 1, in_file) != 1) RETURN_ERROR
		std::vector< uint32_t > char_set(alphabet_size);

		if (fread(char_set.data(), sizeof(uint32_t), alphabet_size, in_file) != (size_t) alphabet_size) RETURN_ERROR
		build_char_map(std::set< uint32_t >(char_set.begin(), char_set.end()));

		size_t pool_size = 0;
		if (fread(&pool_size, sizeof(pool_size), 1, in_file) != 1) RETURN_ERROR
		pool.resize(pool_size);
		if (fread(pool.data(), sizeof(Node), pool_size, in_file) != pool_size) RETURN_ERROR

		fclose(in_file);
		return 0;

#undef RETURN_ERROR
	}
};

#endif // DA_TRIE_HPP

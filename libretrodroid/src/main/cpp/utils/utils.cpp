/*
 *     Copyright (C) 2019  Filippo Scognamiglio
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include <iostream>
#include <fstream>
#include <cstring>
#include <stdexcept>
#include <unistd.h>

#include "utils.h"
#include "../log.h"

namespace libretrodroid {

Utils::ReadResult Utils::readFileAsBytes(const std::string &filePath) {
    // V202: mở nhị phân + đo kích thước bằng tellg; tellg < 0 (không mở được) phải ném lỗi rõ,
    // không được ép sang size_t (thành SIZE_MAX) rồi new char[...] gây bad_alloc/SIGABRT.
    std::ifstream fileStream(filePath, std::ios::binary | std::ios::ate);
    if (!fileStream) {
        throw std::runtime_error("Cannot open game file: " + filePath);
    }
    std::streampos end = fileStream.tellg();
    if (end < 0) {
        throw std::runtime_error("Cannot read game file size: " + filePath);
    }
    size_t size = static_cast<size_t>(end);
    char* bytes = new char[size];
    fileStream.seekg(0, std::ios::beg);
    if (size > 0 && !fileStream.read(bytes, static_cast<std::streamsize>(size))) {
        delete[] bytes;
        throw std::runtime_error("Cannot read game file: " + filePath);
    }
    fileStream.close();

    return ReadResult { size, bytes };
}

Utils::ReadResult Utils::readFileAsBytes(const int fileDescriptor) {
    FILE* file = fdopen(fileDescriptor, "rb");
    if (file == nullptr) {
        close(fileDescriptor);
        throw std::runtime_error("Cannot open game file descriptor");
    }
    size_t size = getFileSize(file);

    char* bytes = new char[size];
    size_t read = size == 0 ? 0 : fread(bytes, sizeof(char), size, file);
    // V202: fclose tự đóng cả fd; trước đây close(fd) để rò FILE*.
    fclose(file);
    if (read != size) {
        delete[] bytes;
        throw std::runtime_error("Cannot read game file descriptor");
    }
    return ReadResult { size, bytes };
}

size_t Utils::getFileSize(FILE* file) {
    if (file == nullptr) return 0;
    fseek(file, 0, SEEK_SET);
    fseek(file, 0, SEEK_END);
    long size = ftell(file);
    fseek(file, 0, SEEK_SET);
    if (size < 0) {
        LOGE("Cannot read file size");
        return 0;
    }
    return static_cast<size_t>(size);
}

const char* Utils::cloneToCString(const std::string &input) {
    char* result = new char[input.length() + 1];
    std::strcpy(result, input.c_str());
    return result;
}

} //namespace libretrodroid
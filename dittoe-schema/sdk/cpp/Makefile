CXX = g++
CXXFLAGS = -Wall -Wextra -std=c++17 -I include
TEST_SRC = tests/test_dittoe.cpp
TEST_BIN = build/test_dittoe

.PHONY: all test clean

all: test

build:
	mkdir -p build

$(TEST_BIN): $(TEST_SRC) include/dittoe.hpp | build
	$(CXX) $(CXXFLAGS) $(TEST_SRC) -o $(TEST_BIN)

test: $(TEST_BIN)
	./$(TEST_BIN)

clean:
	rm -rf build

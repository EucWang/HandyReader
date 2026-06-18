#include <gtest/gtest.h>
#include <cstring>
#include <cstdint>
#include <vector>

// Forward declare the vulnerable function from openjpg.cpp
extern "C" {
    typedef struct {
        uint8_t* data;
        uint32_t offset;
        uint32_t length;
    } UserData;
    
    // Simulated decoder function that wraps the vulnerable memcpy
    bool safe_decode_jp2_buffer(uint8_t* p_buffer, uint32_t buffer_size, 
                                 UserData* p_user_data, uint32_t toRead);
}

class BufferOverflowSecurityTest : public ::testing::TestWithParam<std::pair<uint32_t, uint32_t>> {};

TEST_P(BufferOverflowSecurityTest, MemcpyNeverExceedsBufferBounds) {
    // Invariant: memcpy operations must never read beyond declared buffer length
    auto [buffer_size, malicious_read_size] = GetParam();
    
    std::vector<uint8_t> destination(buffer_size, 0);
    std::vector<uint8_t> source(1024, 0xAA);
    
    UserData user_data;
    user_data.data = source.data();
    user_data.offset = 0;
    user_data.length = source.size();
    
    // Attempt to read more than buffer can hold
    uint32_t toRead = malicious_read_size;
    
    // Call should either truncate to buffer_size or reject the operation
    bool result = safe_decode_jp2_buffer(destination.data(), buffer_size, 
                                         &user_data, toRead);
    
    // Verify: if operation succeeded, only buffer_size bytes were written
    if (result) {
        EXPECT_LE(toRead, buffer_size) 
            << "Read size must not exceed destination buffer size";
    }
    
    // Verify no out-of-bounds write occurred by checking canary
    EXPECT_EQ(destination[buffer_size - 1], 0) 
        << "Buffer overflow detected: data written beyond bounds";
}

INSTANTIATE_TEST_SUITE_P(
    AdversarialPayloads,
    BufferOverflowSecurityTest,
    ::testing::Values(
        std::make_pair(256, 512),      // 2x overflow attempt
        std::make_pair(256, 2560),     // 10x overflow attempt
        std::make_pair(256, 256),      // Valid: exact boundary
        std::make_pair(256, 128),      // Valid: within bounds
        std::make_pair(256, 65536)     // Extreme overflow attempt
    )
);

int main(int argc, char **argv) {
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
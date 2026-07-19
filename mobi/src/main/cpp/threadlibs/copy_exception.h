#pragma warning (disable:4819)
#pragma once
#include <exception>
#include <iostream>
#include <memory>

template<class E>
std::exception_ptr copy_exception(E err)
{
    try{
        throw err;
    } catch(...)
    {
        return std::current_exception();
    }
}
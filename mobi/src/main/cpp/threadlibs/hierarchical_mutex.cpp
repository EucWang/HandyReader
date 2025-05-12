#pragma warning (disable:4819)
#include "hierarchical_mutex.h"
#include <climits>

thread_local unsigned long HierarchicalMutex::this_thread_hierarchy_value_ = ULONG_MAX;

HierarchicalMutex::HierarchicalMutex(unsigned long _hierarchical_value):
	hierarchy_value_(_hierarchical_value),
	previous_hierarchy_value_(0)
{}

void HierarchicalMutex::lock()
{
	check_for_hierarchy_violation();
	internal_mutex_.lock();
	update_hierarchy_value();
}

void HierarchicalMutex::unlock()
{
	this_thread_hierarchy_value_ = hierarchy_value_;
	internal_mutex_.unlock();
}

bool HierarchicalMutex::try_lock()
{
	check_for_hierarchy_violation();
	if (!internal_mutex_.try_lock()) {
		return false;
	}
	update_hierarchy_value();
	return true;
}

void HierarchicalMutex::check_for_hierarchy_violation()
{
	if (this_thread_hierarchy_value_ <= hierarchy_value_) {
		throw std::logic_error("mutex hierarchy violated");
	}
}

void HierarchicalMutex::update_hierarchy_value()
{
	previous_hierarchy_value_ = this_thread_hierarchy_value_;
	this_thread_hierarchy_value_ = hierarchy_value_;
}
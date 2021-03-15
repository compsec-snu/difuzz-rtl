#ifndef __FUZZ_SCHEDULER_H_
#define __FUZZ_SCHEDULER_H_

#include <vector>
#include <string>

class fuzz_scheduler_t
{
public:
    fuzz_scheduler_t(const std::vector<std::string> args);
    ~fuzz_scheduler_t();

    std::string get_elf();
    std::string get_sigfile();
    void yield_control();

private:
    int get_sem(int key, int init);
    void wait_sem();
    void post_sem();
    void remove_sem(int sem);

    std::string output;
    int rtl_sem;
    int iss_sem;
};

#endif // __FUZZ_SCHEDULER_H_

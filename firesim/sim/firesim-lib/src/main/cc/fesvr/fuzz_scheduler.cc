#include "fuzz_scheduler.h"

#include <cstdlib>
#include <ctime>

#include <sys/shm.h>
#include <sys/sem.h>

// Semaphore to synchronize FPGA simulator and
// DifuzzRTL mutator, ISA simulator, and signature checker
union semun {
    int val;
    struct semid_ds *buf;
    unsigned short *array;
    struct seminfo *__buf;
} sem_val;

fuzz_scheduler_t::fuzz_scheduler_t(const std::vector<std::string> args) {
    std::srand(static_cast<unsigned int>(std::time(0)));

    output = std::string("");
    for (auto &arg: args) {
        if (arg.find("+output=") == 0) {
            output = arg.substr(8);
            printf("[FireSim] Fuzz Scheduler, output dir: %s\n", output.c_str());
        }
    }

    if (output.empty()) {
        fprintf(stderr, "[FireSim] output should not be empty\n");
        exit(1);
    }

    int key = 0;
    while (true) {
        key = std::rand();
        try  { iss_sem = get_sem(key, 0); }
        catch (int e) {
            if (e == EEXIST) continue;
            else {
                fprintf(stderr, "get_sem error %d\n", e);
                exit(1);
            }
        }

        try {
            rtl_sem = get_sem(key + 1, 0);
            printf("[FireSim] Fuzz Scheduler, semaphore key: %d\n", key);
            break;
        } catch (int e) {
            if (e == EEXIST) {
                remove_sem(iss_sem);
                continue;
            } else {
                fprintf(stderr, "get_sem error %d\n", e);
                exit(1);
            }
        }
    }
}

fuzz_scheduler_t::~fuzz_scheduler_t() {
    remove_sem(iss_sem);
    remove_sem(rtl_sem);
}

std::string fuzz_scheduler_t::get_elf() {
    return (output + std::string("/.input_0.elf"));
}

std::string fuzz_scheduler_t::get_sigfile() {
    return (output + std::string("/.rtl_sig_0.txt"));
}

int fuzz_scheduler_t::get_sem(int key, int init) {
    int sem = semget(key, 1, IPC_CREAT | 0664);
    if (sem == -1) {
        if (errno == EEXIST) {
            throw errno;
        } else {
            perror("errno");
            fprintf(stderr, "shmget error!\n");
            exit(1);
        }
    } else {
        sem_val.val = init;
        int rc = semctl(sem, 0, SETVAL, sem_val);
        if (rc == -1) {
            fprintf(stderr, "semctl error!\n");
            exit(1);
        }
    }
    return sem;
}

void fuzz_scheduler_t::wait_sem() {
    struct sembuf sem_buf;

    sem_buf.sem_num = 0;
    sem_buf.sem_op = -1;
    sem_buf.sem_flg = 0;

    int rc = semop(rtl_sem, &sem_buf, 1);
}

void fuzz_scheduler_t::post_sem() {
    struct sembuf sem_buf;

    sem_buf.sem_num = 0;
    sem_buf.sem_op = 1;
    sem_buf.sem_flg = 0;

    int rc = semop(iss_sem, &sem_buf, 1);
}

void fuzz_scheduler_t::yield_control() {
    post_sem(); // V (release ISA simulator lock)
    wait_sem(); // P (sleep myself)
}

void fuzz_scheduler_t::remove_sem(int sem) {
    int rc = semctl(sem, 0, IPC_RMID);
    if (rc == -1) {
        fprintf(stderr, "semctl error!\n");
        exit(1);
    }
}

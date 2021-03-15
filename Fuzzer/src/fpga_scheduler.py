import os
import shutil
import sysv_ipc as ipc

class fpgaScheduler():
    def __init__(self, key: int):
        self.iss_sem = ipc.Semaphore(key, ipc.IPC_CREAT, 0x01b4)
        self.rtl_sem = ipc.Semaphore(key + 1, ipc.IPC_CREAT, 0x01b4)

    def wait_sem(self):
        self.iss_sem.P()

    def post_sem(self):
        self.rtl_sem.V()

    def yield_control(self):
        self.post_sem()
        self.wait_sem()

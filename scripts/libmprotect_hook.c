typedef unsigned long size_t;
typedef unsigned long uintptr_t;

#define PROT_READ 0x1
#define PROT_WRITE 0x2
#define PROT_EXEC 0x4

#define MAP_PRIVATE 0x02
#define MAP_FIXED 0x10
#define MAP_ANONYMOUS 0x20

#define SYS_openat 56
#define SYS_close 57
#define SYS_read 63
#define SYS_munmap 215
#define SYS_mmap 222
#define SYS_mprotect 226

#define AT_FDCWD -100

static inline long raw_syscall1(long num, long a0) {
    register long x0 __asm__("x0") = a0;
    register long x8 __asm__("x8") = num;
    __asm__ __volatile__("svc #0" : "+r"(x0) : "r"(x8) : "memory");
    return x0;
}

static inline long raw_syscall2(long num, long a0, long a1) {
    register long x0 __asm__("x0") = a0;
    register long x1 __asm__("x1") = a1;
    register long x8 __asm__("x8") = num;
    __asm__ __volatile__("svc #0" : "+r"(x0) : "r"(x1), "r"(x8) : "memory");
    return x0;
}

static inline long raw_syscall3(long num, long a0, long a1, long a2) {
    register long x0 __asm__("x0") = a0;
    register long x1 __asm__("x1") = a1;
    register long x2 __asm__("x2") = a2;
    register long x8 __asm__("x8") = num;
    __asm__ __volatile__("svc #0" : "+r"(x0) : "r"(x1), "r"(x2), "r"(x8) : "memory");
    return x0;
}

static inline long raw_syscall4(long num, long a0, long a1, long a2, long a3) {
    register long x0 __asm__("x0") = a0;
    register long x1 __asm__("x1") = a1;
    register long x2 __asm__("x2") = a2;
    register long x3 __asm__("x3") = a3;
    register long x8 __asm__("x8") = num;
    __asm__ __volatile__("svc #0" : "+r"(x0) : "r"(x1), "r"(x2), "r"(x3), "r"(x8) : "memory");
    return x0;
}

static inline long raw_syscall6(long num, long a0, long a1, long a2, long a3, long a4, long a5) {
    register long x0 __asm__("x0") = a0;
    register long x1 __asm__("x1") = a1;
    register long x2 __asm__("x2") = a2;
    register long x3 __asm__("x3") = a3;
    register long x4 __asm__("x4") = a4;
    register long x5 __asm__("x5") = a5;
    register long x8 __asm__("x8") = num;
    __asm__ __volatile__("svc #0" : "+r"(x0) : "r"(x1), "r"(x2), "r"(x3), "r"(x4), "r"(x5), "r"(x8) : "memory");
    return x0;
}

static inline void *raw_memcpy(void *dst, const void *src, size_t n) {
    char *d = (char *)dst;
    const char *s = (const char *)src;
    for (size_t i = 0; i < n; i++) d[i] = s[i];
    return dst;
}

__attribute__((visibility("default")))
int mprotect(void *addr, size_t len, int prot) {
    long ret = raw_syscall3(SYS_mprotect, (long)addr, (long)len, (long)prot);
    if (ret == 0) {
        return 0;
    }

    if ((prot & PROT_EXEC) && (ret == -13 || ret == -1)) {
        uintptr_t page_mask = 4095;
        uintptr_t start = (uintptr_t)addr & ~page_mask;
        size_t total_len = ((uintptr_t)addr + len - start + page_mask) & ~page_mask;

        long tmp_map = raw_syscall6(SYS_mmap, 0, (long)total_len, PROT_READ | PROT_WRITE,
                                   MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
        if (tmp_map > 0 && tmp_map != -1) {
            void *tmp = (void *)tmp_map;
            raw_memcpy(tmp, (void *)start, total_len);

            long fixed_map = raw_syscall6(SYS_mmap, (long)start, (long)total_len,
                                         PROT_READ | PROT_WRITE,
                                         MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED, -1, 0);
            if (fixed_map == (long)start) {
                raw_memcpy((void *)start, tmp, total_len);
                raw_syscall2(SYS_munmap, (long)tmp, (long)total_len);

                ret = raw_syscall3(SYS_mprotect, (long)addr, (long)len, (long)prot);
                if (ret == 0) {
                    return 0;
                }
            } else {
                raw_syscall2(SYS_munmap, (long)tmp, (long)total_len);
            }
        }
    }

    return -1;
}

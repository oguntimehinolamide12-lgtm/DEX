# Bundled proot

`app/src/main/jniLibs/<abi>/libproot.so` is the PRoot binary from the Termux
`proot` package, renamed so Android packages it in the native library directory
(which is executable; app storage is not, on modern Android).

- PRoot: https://proot-me.github.io/ — GPL-2.0-or-later
- Termux package: https://github.com/termux/termux-packages/tree/master/packages/proot
- Source: https://github.com/termux/proot

`libtalloc.so` and `libandroid_shmem.so` are the shared libraries proot links
against, from the Termux `libtalloc` and `libandroid-shmem` packages
(LGPL-3.0-or-later and Apache-2.0 respectively). They are copied to the app's
private lib directory under their expected sonames at install time.

The Alpine minirootfs is not redistributed with the app; it is downloaded at
runtime from `https://dl-cdn.alpinelinux.org` and is subject to Alpine's own
licensing.

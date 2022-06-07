# Contributing

Keeping the project small and stable limits our ability to accept new contributors. We are not
seeking new committers at this time, but some small contributions are welcome.

If you've found a bug, please contribute a failing test case so we can study and fix it.

If you have a new feature idea, the library should be extensible enough to allow you to build it
as an add-on (or application of spice) in another project - if it's awesome, we might list it here.
If you need API changes to do so, file an issue and let's talk about it.

Before code can be accepted all contributors must complete our
[Individual Contributor License Agreement (CLA)][cla].

# Code Contributions

Get working code on a personal branch with tests passing before you submit a PR:

1. Install bazelisk (which will act as a version-aware shell for bazel)
   - Package managers:
     - MacOS: `brew install bazelisk`
     - Windows: `choco install bazelisk`
   - Or install directly from [bazelisk release artifacts]

2. build and test: `bazel test //...`

Please make every effort to follow existing conventions and style in order to keep the code as
readable as possible.

Contribute code changes through GitHub by forking the repository and sending a pull request. We
squash pull requests on merge.

[cla]: https://spreadsheets.google.com/spreadsheet/viewform?formkey=dDViT2xzUHAwRkI3X3k5Z0lQM091OGc6MQ&ndplr=1
[bazelisk release artifacts]: https://github.com/bazelbuild/bazelisk/releases
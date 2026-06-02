# Contributing to Synaptiq AI Agent

First off, thank you for considering contributing to Synaptiq AI Agent! It's people like you that make Synaptiq such a great tool.

## Where do I go from here?

If you've noticed a bug or have a feature request, make sure to check our [Issues](https://github.com/testencomnom-collab/synaptiq-ai-agent/issues) page to see if someone else has already created a ticket. If not, go ahead and make one!

## Fork & create a branch

If this is something you think you can fix, then fork Synaptiq AI Agent and create a branch with a descriptive name.

A good branch name would be (where issue #325 is the ticket you're working on):

```sh
git checkout -b 325-add-new-scroll-action
```

## Get the test suite running

Make sure your changes are tested! We encourage adding Unit Tests or Instrumented Tests for any new UI interactions or Agent Actions.

## Implement your fix or feature

At this point, you're ready to make your changes! Feel free to ask for help; everyone is a beginner at first.

## Make a Pull Request

At this point, you should switch back to your master branch and make sure it's up to date with Synaptiq's master branch:

```sh
git remote add upstream git@github.com:testencomnom-collab/synaptiq-ai-agent.git
git checkout main
git pull upstream main
```

Then update your feature branch from your local copy of main, and push it!

```sh
git checkout 325-add-new-scroll-action
git rebase main
git push --set-upstream origin 325-add-new-scroll-action
```

Finally, go to GitHub and make a Pull Request.

## Keeping your Pull Request updated

If a maintainer asks you to "rebase" your PR, they're saying that a lot of code has changed, and that you need to update your branch so it's easier to merge.

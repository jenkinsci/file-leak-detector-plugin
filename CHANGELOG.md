## Changelog

### Version 1.6 (November 2, 2018)

[JENKINS-53394](https://issues.jenkins.io/browse/JENKINS-53394): Avoid killing Jenkins when the arguments are wrong

### Version 1.5 (April 16, 2018)

Add support for detecting file descriptors used by the following resources:

- Files opened via `java.nio.channels.FileChannel#open`
- Selectors opened via `java.nio.channels.Selector#open`
- Pipes opened via `java.nio.channels.Pipe#open`(Unix only)

### Version 1.4 (June 08, 2015)

Metadata fixed

### Version 1.3 (September 11, 2013)

?

### Version 1.2 (April 3, 2012)

Improved compatibility with the monitoring plugin by forking a separate JVM to do the attachment

### Version 1.1 (April 3, 2012)

First public release

# Java network proxy
A proxy-type thing for simple communication between applets.

## What is it?
I needed to do some very simple networking between a given number of applets.
Thus, I created this library to do the hard work for you.

It has configurable ping times, timeouts and connection retry delay.

The main `Proxy` class is a generic class initialised once with a message deserialised.
After this, simply specify the listen host (or null) and port, and connect to peers.

There is also an option to load a configuration from a JSON file.
Requires the Google GSON library.

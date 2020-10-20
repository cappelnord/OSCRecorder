# OSCRecorder
Simple GUI to record and playback OSC streams with SuperCollider. 

## Install as Quark

In SuperCollider evaluate this code snippet:

```sclang
Quarks.install("https://github.com/cappelnord/OSCRecorder.git");
// recompile class library afterwards ...
```

## Manual installation

* Download the code from GitHub or clone the repository on your local drive
* In SuperCollider locate your user support directory (File>Open user support directory)
* If there isn't a directory called *Extensions* in there create one
* Copy the OSCRecorder directory into the Extensions directory
* Recompile class library (Language>Recompile Class Library)

## Usage

Just run this code:

```sclang
OSCRecorderGUI();
```
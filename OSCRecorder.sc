OSCRecorderGUI {
	var win;

	var <player;
	var <recorder;

	var oscPortInput;
	var recordButton;
	var recordStatus;
	var saveButton;
	var loadButton;
	var loopCheckBox;
	var ipInput;
	var portInput;
	var playButton;
	var playStatus;


	*new {
		^super.new.init();
	}

	init {
		win = Window("OSCRecorder", Rect(300, 300, 640, 340), true);

		oscPortInput = TextField().string_(NetAddr.langPort); // no action
		recordButton = Button().states_([["Record", Color.black, Color.red], ["Stop", Color.black, Color.yellow]]);
		recordButton.action_({this.pr_actionRecordButton;});

		recordStatus = StaticText().string_(0);

		saveButton = Button().states_([["Save", Color.black, Color.grey], ["Save", Color.black, Color.green]]);
		saveButton.action_({this.pr_actionSaveButton;});

		ipInput = TextField().string_("127.0.0.1");
		portInput = TextField().string_(NetAddr.langPort);

		loadButton = Button().states_([["Load", Color.black, Color.green]]);
		loadButton.action_({this.pr_actionLoadButton;});


		loopCheckBox = CheckBox().value_(true);
		playButton = Button().states_([["Play", Color.black, Color.grey], ["Play", Color.black, Color.green], ["Stop", Color.black, Color.red]]);

		playButton.action_({this.pr_actionPlayButton;});

		playStatus = StaticText().string_("No file loaded ...");

		win.layout_(
			VLayout(
				HLayout(StaticText().string_(">>> Recording:")),
				HLayout(StaticText().string_("Incoming Port:"), oscPortInput, recordButton),
				HLayout(StaticText().string_("Messages recorded:"), recordStatus, saveButton),
				HLayout(StaticText().string_(" ")),
				HLayout(StaticText().string_(">>> Playback:")),
				HLayout(loadButton),
				HLayout(StaticText().string_("Destination:"), ipInput, portInput),
				HLayout(StaticText().string_("Loop"), loopCheckBox, playButton),
				HLayout(playStatus)
			)
		);

		win.onClose_({
			recorder.stop;
			recorder = nil;
			player.stop;
			player = nil;
		});

		win.front;
	}

	pr_actionRecordButton {
		(recordButton.value == 1).if {
			// start Recording
			recorder = OSCRecorder(oscPortInput.value.asInt, false);
			recorder.addDependant(this);
			recorder.start;
			saveButton.value = 0;
			^this;
		};
		(recordButton.value == 0).if {
			// stop Recording
			recorder.stop;
			saveButton.value = 1;
			^this;
		};
	}

	pr_actionSaveButton {
		(saveButton.value == 1).if {
			saveButton.value = 0;
			^this;
		};
		(saveButton.value == 0).if {
			saveButton.value = 1;
			FileDialog({|files|
				var filename = files[0];
				recorder.save(filename);
			}, fileMode: 0, acceptMode: 1);
			^this;
		}
	}

	pr_actionLoadButton {
		FileDialog({|files|
			var filename = files[0];
			player = OSCPlayer.load(filename, this.getDestination);
			player.isNil.not.if ({
				player.addDependant(this);
				playStatus.string_("Messages loaded: " ++ player.list.size);
				playButton.value = 1;
			}, {
				playStatus.string_("Could not open file!");
				playButton.value = 0;
			});
		}, nil, 1, 0);
	}

	pr_actionPlayButton {
		(playButton.value == 1).if {
			^nil;
		};
		(playButton.value == 2).if {
			var times = 1;
			(loopCheckBox.value == true).if {
				times = inf;
			};
			player.destination = this.getDestination;
			player.play(times);
			^this;
		};
		(playButton.value == 0).if {
			player.stop();
			playButton.value = 1;
			^this;
		}
	}

	getDestination {
		var host = ipInput.string;
		var port = portInput.string.asInt;
		^NetAddr(host, port);
	}

	update {|obj, what|

		(obj == recorder).if {
			recordStatus.string_(obj.list.size);
			^this;
		};

		((obj == player) && (what == "progressPlaying")).if {
			(playButton.value == 1).if {
				^nil;
			};
			playStatus.string_("" ++ player.progress + "/" + player.list.size);
			^this;
	    };

		((obj == player) && (what == "stopPlaying")).if {
			{
				playButton.value = 1;
				playStatus.string_("Playback finished ...");
			}.defer;
			^this;
		};
	}

}

OSCRecorder {

	var <list;
	var port;
	var oscFunc;

	var tickSched;

	var useBundleTime;

	*new {|port=57120, useBundleTime=false|
		^super.new.init(port, useBundleTime);
	}

	init {|a_port, a_useBundleTime|
		port = a_port;
		useBundleTime = a_useBundleTime;
	}

	pr_tickProgress {
		tickSched.isNil.if {
			{
				this.changed("progressRecording");
				tickSched = true;
				{
					this.changed("progressRecording");
					tickSched = nil;
				}.defer(0.25);
			}.defer;
		}
	}

	start {
		oscFunc.isNil.not.if {
			"OSCRecorder already recording!".error;
			^this;
		};

		list = List.new();

		"OSC Recording started ...".postln;
		this.changed("startRecording");

		thisProcess.openUDPPort(port);

		oscFunc = {|msg, time, addr, recvPort|
			useBundleTime.not.if {
				time = Main.elapsedTime;
			};

			// apply some sort of filter?
			(recvPort == port).if {
				list.add([time, msg]);
				this.pr_tickProgress;
			};
		};

		thisProcess.addOSCRecvFunc(oscFunc);

	}

	stop {
		oscFunc.isNil.if {
			"OSCRecorder not recording!".error;
			^this;
		};
		"OSC Recording stopped ...".postln;
		this.changed("stopRecording");

		thisProcess.removeOSCRecvFunc(oscFunc);
		oscFunc = nil;
	}

	save {|filename|
		var timeBase, string, file;

		oscFunc.isNil.not.if {
			this.stop;
		};

		(list == nil || list.size == 0).if {
			"Nothing recorded. Cannot save!".error;
			^this;
		};

		timeBase = list.first[0];
		list.do {|item| item[0] = item[0] - timeBase;};

		string = list.cs;

		file = File(filename, "w");

		(file.isOpen == false).if {
			("Could not write to file: '" ++ filename ++ "' ...").error;
			^this;
		};

		file.write(string);
		file.close();
		("Wrote to file: '" ++ filename ++ "' ...").postln;

	}
}

OSCPlayer {

	var task;
	var <list;
	var <>destination;
	var <progress = 0;

	var tickSched;

	*new {|list, destination|
		^super.new.init(list, destination);
	}

	*load {|filename, destination|
		var string;
		var file = File(filename, "r");
		var list;
		file.isOpen.not.if {
			("Could not open file: '" ++ filename ++ "' ...").error;
			^nil;
		};
		string = file.readAllString;
		file.close();
		list = string.interpret;
		list.isNil.if {
			"File is no valid OSC file!".error;
			^nil;
		};
		^this.new(list, destination);
	}


	pr_tickProgress {
		tickSched.isNil.if {
			{
				this.changed("progressPlaying");
				tickSched = true;
				{
					this.changed("progressPlaying");
					tickSched = nil;
				}.defer(0.25);
			}.defer;
		}
	}

	init {|a_list, a_destination|
		list = a_list;
		destination = a_destination;
	}

	play {|times=1|
		task.isNil.not.if {
			"OSCPlayer already playing!".error;
			^this;
		};

		task = Routine({
			this.changed("startPlaying");
			times.do {
				var lastTime = list[0][0];
				list.do {|item, i|
					(item[0] - lastTime).wait;
					lastTime = item[0];
					destination.sendMsg(*item[1]);
					progress = i+1;
					this.pr_tickProgress;
				};
			};
			"Finished playing ...".postln;
			{this.stop;}.defer;
		}).play(TempoClock.default);
	}

	stop {
		task.stop;
		task = nil;
		this.changed("stopPlaying");
	}
}
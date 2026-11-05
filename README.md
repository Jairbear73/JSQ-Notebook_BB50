# JSQ Notebook
## For BlackBerry OS 5.0
(C) Copyright JSQ Systems 2026

This is a OneNote inspired note taking application, with markdown style editing, and full rich text & multimedia support.
All code strictly uses unsigned apis, meaning no keys are required to compile, and has been optimised to run as fast as possible.

### Features:

**Notebooks**
Notes are stored in notebooks similar to OneNote. Each notebook contains sections, and sections contain pages.

**Customisable Fields**
Each page by default has a title and content field. However you can change the default fields for a section, including adding custom fields, or change fields for just one page.

**Formatting**
JSQ Notebook uses markdown stformatting for rich text. This allows for easy formatting of text, including bold, italic, underline, strikethrough, as well as more advanced features such as:
> Code blocks
> Tables
> Quotes
> Checkboxes
> Lists
...and many other things you'd find in markdown and onenote.

*Multimedia* content such as images and audio files can also be embedded in pages.

**View Mode**
Your beautiful markdown formatting can then be viewed in *"View Mode"*, which renders the rich formatting seamlessly for easy scrolling, and will even display images inline! Embedded content can be clicked on and will attempt to open in the appropriate external app.

Clicking on text will send you straight to edit mode at that position in the text.
You can also scroll per screen using space/shift+space.

**Dark Mode Support**
Yep thats right, dark mode!! If you have a theme that uses a dark background you will be pleased that this app caters to it.
When enabled through options, the text will become white, and all colour intensities will be inverted to display well. This will not change the way the files are stored, so they will display normally on other devices.

**Import/Export**
All of your notebooks can be exported to a custom .jnb file and directory structure to the SD Card using unsigned apis. Eventually the plan is to create a seperate project to sync this with OneNote, or even a dedicated desktop client. The files use html compatible format making them extreemly portable.

Importing these files is also supported.


### Installation:

Prebuilt packages have been provided at the root of this repository. Simply install them as you would any other application. I highly recommend using *vnbbUtils* to sideload the cod via usb.


### Building:

You can either use the BlackBerry JDE v5.0 to open the project, or load this repo up in vscode, and edit the provided macro to automatically build it for you.


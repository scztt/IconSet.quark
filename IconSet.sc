IconSet {
    classvar <>pixelRatio = 2, rootPaths, iconQuarks;
    
    *iconUrl { 
        this.subclassResponsibility(thisMethod).throw;
    }
    
    *iconUrl_{ 
        this.subclassResponsibility(thisMethod).throw; 
    }
    
    *icons { 
        this.subclassResponsibility(thisMethod).throw 
    }
    
    *initClass {
        Class.initClassTree(Quark);
        Class.initClassTree(Quarks);
        
        rootPaths = ();
        iconQuarks = ();
        
        this.subclasses.do {
            |subclass|
            if (subclass.quark.isInstalled) {
                (
                    "The icon quark '%' is installed. This can cause SuperCollider to recompile much more slowly. "
                        ++ "This quark must to be downloaded but should not be installed. Uninstall using: '%.quark.uninstall'"
                ).format(subclass.name, subclass.name).warn;
            };
        }
    }
    
    *quark {
        var q;
        ^(iconQuarks[this] ?? {
            iconQuarks[this] = q = Quark(this.iconUrl);
            q;
        })
    }
    *fetch { ^this.checkout }
    *checkout {
        "*** Fetching icons for % (%);".format(this.name, this.quark.url).postln;
        "*** This may take a while...".format(this.name, this.quark.url).postln;
        this.quark.checkout;
        "*** Icons downloaded to %".format(this.quark.localPath).postln;
    }
    *update {
        this.quark.fetch;
        this.quark.update;
    }
    
    *rootPath {
        var p;
        
        if (this.quark.isDownloaded.not) {
            Error("Icons not installed for %. Install icons usng: '%.fetch'".format(this.name, this.name)).throw
        } {
            ^(rootPaths[this] ?? {
                rootPaths[this] = p = PathName(this.quark.localPath).fullPath;
                p;
            });
        }
    }
    
    *new {
        |name, height=64, color=nil, format='svg'|
        color = color ?? QtGUI.palette.buttonText;
        switch (
            format.asString.toLower.asSymbol,
            { 'png' }, { ^this.newPNG(name, height, color) },
            { 'svg' }, { ^this.newSVG(name, height, color) },
        )
    }
    
    *newPNG {
        |name, height=64, color=nil|
        var path, image;
        
        path = this.findFile(name, \png, height);
        image = Image.open(path).pixelRatio_(pixelRatio);
        ^this.resizedTintedImage(image, height * pixelRatio, color);
    }
    
    *newSVG {
        |name, height=64, color=nil|
        var path, image;
        
        path = this.findFile(name, \svg, height);
        image = Image.openSVG(path, (height * pixelRatio).asSize).pixelRatio_(pixelRatio);
        ^this.resizedTintedImage(image, height * pixelRatio, color);
    }
    
    *browse {
        var icons = this.icons;
        var view, outerView, color;
        
        outerView = ScrollView(bounds:600@800);
        outerView.canvas = (
            View().layout_(HLayout(
                view = View(),
                nil
            ).spacing_(0).margins_(9))
        );
        view.layout_(GridLayout());
        
        color = (QtGUI.palette.window.asHSV[2] < 0.5).if({
            Color(0.9, 0.9, 0.9)
        }, {
            Color(0.1, 0.1, 0.1)
        });
        
        icons.do {
            |name, i|
            var icon;
            try {
                icon = this.new(name.asString, 32, color, format:'svg')
            } {
                "Could not find icon: %".format(name).warn;
            };
            
            if (icon.notNil) {
                view.layout.add(
                    View().layout_(
                        StackLayout(
                            DragSource()
                                .fixedSize_(38@38)
                                .string_("")
                                .background_(Color.clear)
                                .object_("%(\"%\")".format(this.name, name))
                                .string_(""),
                            View().canFocus_(false).fixedSize_(38@38).setBackgroundImage(icon, 16),
                        ).mode_(\stackAll)
                    ),
                    (i / 16).floor,
                    (i % 16),
                    \center
                )
            }
        };
        
        outerView.front;
    }
    
    *resizedTintedImage {
        |image, height, color|
        var oldRect, newImage, newRect, colorImage, pixelRatio;
        
        pixelRatio = image.pixelRatio;
        
        oldRect = image.bounds;
        
        if ((oldRect.height == height) && color.isNil) {
            ^image
        } {
            newRect = Rect(0, 0, height.asInteger, height.asInteger);
            newImage = Image(newRect.width, newRect.height);
            newImage.pixelRatio = 1;
            image.pixelRatio = 1;
            
            if (color.notNil) {
                colorImage = Image(1, 1).fill(color);
            };
            
            newImage.draw({
                image.drawInRect(newRect); // coords to account for real pixels vs device pixels
                if (colorImage.notNil) {
                    colorImage.drawInRect(newRect, operation:'sourceIn');
                }
            });
            
            newImage.pixelRatio = pixelRatio;
            
            ^newImage
        }
    }
}

Material : IconSet {
    classvar <>iconUrl = "https://github.com/google/material-design-icons.git",
        folders;
    
    *icons {
        |type|
        var icons = IdentitySet();
        this.folders.do {
            |folder|
            PathName(this.rootPath +/+ folder +/+ "svg" +/+ "production").files.do {
                |file|
                var replacements = ["26x24px.svg", "12px.svg", "18px.svg", "24px.svg", "36px.svg", "48px.svg"];
                var name = file.fileName;
                
                replacements.do {
                    |re|
                    name = name.replace(re, "");
                };
                if (name.endsWith("_")) {
                    name = name[0..name.size-2]
                };
                if (name.beginsWith("ic_")) {
                    name = name[3..]
                };
                
                icons.add(name.asSymbol);
            }
        };
        ^icons.asArray.sort
    }
    
    
    *folders {
        ^(folders ?? {
            folders = (this.rootPath +/+ "*").pathMatch;
            folders = folders.collect(PathName(_)).select(_.isFolder);
            folders = folders.collect({ |f| f.folderName });
            folders = folders.reject({
                |name|
                name.contains(".") or: { [\iconfont, \sprites].includes(name.asSymbol) }
            });
        })
    }
    
    *findFile {
        |name, extension, sizeHint|
        var categoryFolder, rootPath, paths, postFixList, subfolder, result;
        
        name = "ic_" ++ name;
        
        categoryFolder = switch(extension,
            \svg, "svg/production",
            \png, "drawable-xxxhdpi"
        );
        
        rootPath = this.rootPath;
        if (extension == \svg) {
            postFixList = (48: "_48px", 24: "_24px", 26: "_26x24px");
        } {
            postFixList = (
                18: "_black_18dp", 24: "_black_24dp", 36:"_black_36dp", 48:"_black_48dp"
            );
        };
        postFixList = postFixList.keys.asArray.sort({
            |a, b|
            a = a - sizeHint; b = b - sizeHint;
            if (a.isPositive && b.isPositive) {
                a < b
            } {
                a > b
            }
        }).collect(postFixList[_]);
        
        paths = postFixList.collect({
            |postFix|
            this.folders.collect({
                |folder|
                var path;
                
                path = rootPath +/+ folder +/+ categoryFolder;
                path = path +/+ name ++ postFix ++ "." ++ extension;
            });
        }).flatten(1);
        
        result = paths.detect(File.exists(_));
        
        if (result.notNil) {
            ^result
        } {
            Error("Couldn't find icon with name '%' in paths: %".format(name, paths.join(", "))).throw;
        }
    }
}

Linea : IconSet {
    classvar <>iconUrl = "https://github.com/linea-io/Linea-Iconset.git";
    
    *folders {
        ^["_arrows", "_basic", "_basic_elaboration", "_ecommerce", "_music", "_software", "_weather"];
    }
    
    *icons {
        |type|
        var icons = IdentitySet();
        this.folders.do {
            |folder|
            PathName(this.rootPath +/+ folder +/+ "_PNG64").files.do {
                |file|
                icons.add(file.fileNameWithoutDoubleExtension.asSymbol);
            }
        };
        ^icons.asArray.sort
    }
    
    *findFile {
        |name, extension, sizeHint|
        var categoryFolder, rootPath, paths, result;
        
        categoryFolder = switch(extension,
            \svg, "_SVG expanded",
            \png, "_PNG64"
        );
        
        rootPath = this.rootPath;
        
        paths = this.folders.collect({
            |folder|
            rootPath +/+ folder +/+ categoryFolder +/+ name ++ "." ++ extension
        });
        
        result = paths.detect(File.exists(_));
        
        if (result.notNil) {
            ^result
        } {
            Error("Couldn't find icon with name '%' in paths: %".format(name, paths.join(", "))).throw;
        }
    }
    
}
var filenameExtRegex = /(?:\.([^.]+))?$/;
var logListRequest = null;
var bugListRequest = null;
var bugRequest = null;
var defaultAjaxType = "GET";

var currentBugId = 0;
var currentCommandLog = null;
var currentLogName = null;

function refreshLogList() { 
    if (logListRequest != null)
    {
        logListRequest.abort();
        logListRequest = null;
    }
    else
    {
        $("#log-list-loading-indicator").addClass("show");
    }
    
    logListRequest = $.ajax({
        dataType: "json",
        url: "/get_log_list",
        type: defaultAjaxType,
        success: function(data) {
            var list = $("#log-list").empty();
            if (data.log_id)
            {
                for (var i = 0; i < data.log_id.length; ++ i) {
                    list.append("<div class='log-item' log-id='" + data.log_id[i] + "'>" + data.name[i] + "</div>");
                }
            }

            logListRequest = null;
            $("#log-list-loading-indicator").removeClass("show");
        },
        error: function() {
            $("#log-list").text("Error occured when retrieving the log list");
            logListRequest = null;
            $("#log-list-loading-indicator").removeClass("show");
        }});
}

function refreshBugList(logId, logName) { 
    if (bugListRequest != null)
    {
        bugListRequest.about();
        bugListRequest = null;
    }
    else
    {
        $("#bug-list-loading-indicator").addClass("show");
    }
    
    $("#bug-list-log-name").text(logName);
    bugListRequest = $.ajax({
        dataType: "json",
        url: "/get_bug_list",
        type: defaultAjaxType,
        data: { log_id: logId },
        success: function(data) {
            var list = $("#bug-list").empty();
            if (data.bug_id)
            {
                for (var i = 0; i < data.bug_id.length; ++ i) {
                    list.append("<div class='bug-item' log-id='" + logId + "' bug-id='" + data.bug_id[i] + "'>" + data.name[i] + "</div>");
                }
            }

            bugListRequest = null;
             $("#bug-list-loading-indicator").removeClass("show");
        },
        error: function() {
            $("#bug-list").text("Error occured when retrieving the bug list");
            bugListRequest = null;
            $("#bug-list-loading-indicator").removeClass("show");
        }});
}

function refreshBug(logId, logName, bugId) { 
    if (bugRequest != null)
    {
        bugRequest.about();
        bugRequest = null;
    }
    else
    {
        $("#bug-loading-indicator").addClass("show");
    }
    
    currentCommandLog = null;
    currentLogName = null;
    currentBugId = null;

    $("#bug-title").text(logName + ":" + bugId);
    bugRequest = $.ajax({
        dataType: "json",
        url: "/get_bug",
        type: defaultAjaxType,
        data: { log_id: logId, bug_id: bugId },
        success: function(data) {
            currentCommandLog = data["command log"];
            currentLogName = logName;
            currentBugId = bugId;
            var container = $("#bug").empty();
            var logcat = $("<div class='bug-logcat'></div>"); 
            container
                .append("<h3>Basic Info</h3>")
                .append($("<ul/>")
                        .append($("<li/>").text("Command Log: ").append($("<a href='#' class='command-log-link'/>").text(data["command log"])))
                        .append($("<li/>").text("Type: " + (data["checker"] ? data["checker"] : data["exception"])))
                        .append($("<li/>").text("Device Version: " + data["device version"]))
                        .append($("<li/>").text("Device Config: " + data["device config"]))
                        .append($("<li/>").text("Apk Path: " + data["apk path"]))
                       );
            if (data["attached file"]) {
                var fileInfo = data["attached file"];
                container.append("<h3>Attachment</h3>");
                var fileList = $("<ul/>");
                for (var i in fileInfo)
                {
                    var entry = fileInfo[i];
                    if (entry[0] != "attachment path") continue;
                    fileList.append($("<li/>").append($("<a href='#' class='attachment-link'/>").text(entry[1])));
                }
                container.append(fileList);
            }
            container.append($("<div class='bug-logcat'></div>").text(data["recent logcat"]));
            
            bugRequest = null;
             $("#bug-loading-indicator").removeClass("show");
        },
        error: function() {
            $("#bug").text("Error occured when retrieving the bug");
            bugRequest = null;
            $("#bug-loading-indicator").removeClass("show");
        }});
}

function attachmentImgLoad(ev)
{
    $(ev.currentTarget).show();
}

function createAsyncPre(filename)
{
    var txtBox = $("<div class='txt-box'></div>");
    $.ajax({
        dataType: "text",
        url: "/static/" + filename,
        type: defaultAjaxType,
        success: function(data) {
            txtBox.text(data).show();
        },
        error: function() {
            txtBox.text("Error occured when retrieving the txt file");
        }});
    return txtBox;
}

$(document).ready(function() {
    refreshLogList();
    $("#refresh-log-list").click(function () { refreshLogList(); return false; });
    $("#log-list").delegate(".log-item", "click", function (ev) {
        var target = $(ev.currentTarget);
        var logId = target.attr("log-id");
        refreshBugList(logId, target.text());
        return false;
    });
    $("#bug-list").delegate(".bug-item", "click", function (ev) {
        var target = $(ev.currentTarget);
        var logId = target.attr("log-id");
        var bugId = target.attr("bug-id");
        refreshBug(logId, $("#bug-list-log-name").text(), bugId);
        return false;
    });
    $("#bug").delegate(".command-log-link", "click", function (ev) {
        var target = $(ev.currentTarget);
        var filename = target.text();
        if (target.prop("txt-inserted") != "yes")
        {
            target.prop("txt-inserted", "yes");
            target.parent().append("<br/>").append(createAsyncPre(filename));
        }
        return false;
    });
    $("#bug").delegate(".attachment-link", "click", function (ev) {
        var target = $(ev.currentTarget);
        var filename = target.text();
        var ext = filenameExtRegex.exec(filename)[1];
        if (ext == "png")
        {
            if (target.prop("img-inserted") != "yes")
            {
                target.prop("img-inserted", "yes");
                target.parent().append("<br/>").append($("<img class='attachment-img' src='" + filename + "'/>").load(attachmentImgLoad));
            }
        }
        else if (ext == "txt")
        {
            if (target.prop("txt-inserted") != "yes")
            {
                target.prop("txt-inserted", "yes");
                target.parent().append("<br/>").append(createAsyncPre(filename));
            }
        }        
        return false;
    });
    $("#bug-reproduce-cmd").click(function(ev) {
        if (currentCommandLog == null || currentCommandLog.length == 0) 
        {
            alert("no command log");
        }
        else
        {
            $.ajax({
                dataType: "text",
                url: "/reproduce_bug",
                data: { log_name: currentLogName, command_log_name: currentCommandLog, bug_id: currentBugId },
            });
        }
        return false;
    });
});

#!/usr/bin/env groovy

/**
* notify slack and set message based on build status
* fork from https://danielschaaff.com/2018/02/09/better-jenkins-notifications-in-declarative-pipelines/
*/
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.model.Actionable;

def call(String buildStatus = 'STARTED', String channel = null) {
    // buildStatus of null means successfull
    buildStatus = buildStatus ?: 'SUCCESSFUL'

    // Default values
    def colorCode = 'danger'
    def subject = "${buildStatus}: Job '${env.JOB_NAME}' (<${env.RUN_CHANGES_DISPLAY_URL}|Changes>)"
    def title = "${env.JOB_NAME}, build #${env.BUILD_NUMBER}"
    def title_link = "${env.RUN_DISPLAY_URL}"
    def branchName = "${env.BRANCH_NAME}"

    def commit = sh(returnStdout: true, script: 'git rev-parse HEAD')
    def author = sh(returnStdout: true, script: "git --no-pager show -s --format='%an'").trim()

    def message = sh(returnStdout: true, script: 'git log -1 --pretty=%B').trim()

    // Override default values based on build status
    if (buildStatus == 'STARTED') {
        colorCode = '#2ecc71'
    } else if (buildStatus == 'SUCCESSFUL') {
        colorCode = 'good'
    } else if (buildStatus == 'UNSTABLE') {
        colorCode = 'warning'
    } else {
        colorCode = 'danger'
    }

    // get test results for slack message
    @NonCPS
    def getTestSummary = { ->
        def testResultAction = currentBuild.rawBuild.getAction(AbstractTestResultAction.class)
        def summary = ""

        if (testResultAction != null) {
            def total = testResultAction.getTotalCount()
            def failed = testResultAction.getFailCount()
            def skipped = testResultAction.getSkipCount()

            summary = "Test results:\n\t"
            summary = summary + ("Passed: " + (total - failed - skipped))
            summary = summary + (", Failed: " + failed + " ${testResultAction.failureDiffString}")
            summary = summary + (", Skipped: " + skipped)
        } else {
            summary = null
        }
        return summary
    }
    def testSummaryRaw = getTestSummary()
    // format test summary as a code block
    def testSummary = "```${testSummaryRaw}```"
    // println testSummary.toString()

    JSONObject attachment = new JSONObject();
    attachment.put('title', title.toString());
    attachment.put('title_link',title_link.toString());
    attachment.put('text', subject.toString());
    attachment.put('fallback', "fallback message");
    attachment.put('color',colorCode);
    attachment.put('mrkdwn_in', ["fields"])
    // JSONObject for branch
    JSONObject branch = new JSONObject();
    branch.put('title', 'Branch');
    branch.put('value', branchName.toString());
    branch.put('short', true);
    // JSONObject for author
    JSONObject commitAuthor = new JSONObject();
    commitAuthor.put('title', 'Author');
    commitAuthor.put('value', author.toString());
    commitAuthor.put('short', true);
    // JSONObject for branch
    JSONObject commitMessage = new JSONObject();
    commitMessage.put('title', 'Commit Message');
    commitMessage.put('value', message.toString());
    commitMessage.put('short', false);
    // JSONObject for test results
    JSONObject testResults = new JSONObject();
    testResults.put('title', 'Test Summary')
    testResults.put('value', testSummary.toString())
    testResults.put('short', false)

    def fields = []
    if (branchName != 'null') {
        fields.push(branch)
    }
    fields.push(commitAuthor)
    fields.push(commitMessage)
    if (testSummaryRaw) {
        fields.push(testResults)
    }
    attachment.put('fields', fields);
    JSONArray attachments = new JSONArray();
    attachments.add(attachment);
    // println attachments.toString()

    // Send notifications
    if (channel) {
        slackSend (color: colorCode, message: subject, attachments: attachments.toString(), channel: channel)
    } else {
        slackSend (color: colorCode, message: subject, attachments: attachments.toString())
    }
}
package net.dontdrinkandroot.sara

enum class Mode(val label: String, val instruction: String) {
    PLAN(
        label = "plan",
        instruction = "The user has activated PLAN MODE. You must not modify the system state " +
                "through any tool. You may only read, analyze, observe, and plan. " +
                "`exec_command` is restricted to read-only inspection commands — do not run " +
                "anything that creates, modifies, deletes, installs, removes, starts, stops, " +
                "enables, or disables (e.g. no `rm`, `mv`, `mkdir`, `touch`, `>`/`>>`, `tee`, " +
                "`apt install`, `systemctl start/stop/enable`, `kill`, `chmod`, `chown`, " +
                "`useradd`). When you identify changes that should be made, describe them " +
                "clearly so the user can execute them manually or by switching to execution " +
                "mode with /exec."
    ),
    EXEC(
        label = "exec",
        instruction = "The user has activated EXECUTION MODE. You may now perform system " +
                "modifications using available tools."
    )
}

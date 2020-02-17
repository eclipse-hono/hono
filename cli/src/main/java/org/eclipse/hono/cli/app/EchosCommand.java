//package org.eclipse.hono.cli.app;
//
//import org.eclipse.hono.cli.shell.InputReader;
//import org.eclipse.hono.cli.shell.ShellHelper;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.shell.standard.ShellComponent;
//import org.springframework.shell.standard.ShellMethod;
//import org.springframework.shell.standard.ShellOption;
//import org.springframework.util.StringUtils;
//
//import java.util.Arrays;
//import java.util.HashMap;
//import java.util.Map;
//
//@ShellComponent
//public class EchosCommand {
//
//    @ShellMethod("Displays greeting message to the user whose name is supplied")
//    public String echo(@ShellOption({"-N", "--name"}) String name) {
//        String message = String.format("Hello %s!", name);
//        shellHelper.print(message.concat(" (Default style message)"));
//        shellHelper.printError(message.concat(" (Error style message)"));
//        shellHelper.printWarning(message.concat(" (Warning style message)"));
//        shellHelper.printInfo(message.concat(" (Info style message)"));
//        shellHelper.printSuccess(message.concat(" (Success style message)"));
//
//        String output = shellHelper.getSuccessMessage(message);
//        return output.concat(" You are running spring shell cli-demo.");
//    }
//
//    @ShellMethod("Create new user with supplied username")
//    public void createUser(@ShellOption({"-U", "--username"}) String username) {
//
//        shellHelper.printInfo("Please enter new user data:");
//        // 1. read user's fullName --------------------------------------------
//        String fullName = inputReader.prompt("Full name");
//
//        // 2. read user's password --------------------------------------------
//        String password = inputReader.prompt("Password", "secret", false);
//        if (StringUtils.hasText(password)) {
//            shellHelper.printWarning("Password'CAN NOT be empty string? Please enter valid value!");
//        }
//
//        // 3. Prompt for user's Gender ----------------------------------------------
//        Map<String, String> options = new HashMap<>();
//
//        String genderValue = inputReader.selectFromList("Gender", "Please enter one of the [] values", options, true, null);
//        // 4. Prompt for superuser attribute
//        String superuserValue = inputReader.promptWithOptions("New user is superuser", "N", Arrays.asList("Y", "N"));
//
//        // Print user's input -------------------------------------------------
//        shellHelper.printInfo("\nCreating a new user:");
//
//        shellHelper.printSuccess("---> SUCCESS created user with ");
//    }
//
//}

//    CLASS EXAMPLE
//
//    @ShellMethod("Displays greeting message to the user whose name is supplied")
//    public String echo(@ShellOption({"-N", "--name"}) String name) {
//        String message = String.format("Hello %s!", name);
//        shellHelper.print(message.concat(" (Default style message)"));
//        shellHelper.printError(message.concat(" (Error style message)"));
//        shellHelper.printWarning(message.concat(" (Warning style message)"));
//        shellHelper.printInfo(message.concat(" (Info style message)"));
//
//        String fullName = inputReader.prompt("Full name");
//        String password = inputReader.prompt("Password", "secret", false);
//        Map<String, String> options = new HashMap<>();
//        String genderValue = inputReader.selectFromList("Gender", "Please enter one of the [] values", options, true, null);
//        String superuserValue = inputReader.promptWithOptions("New user is superuser", "N", Arrays.asList("Y", "N"));
//
//        String output = shellHelper.getSuccessMessage(message);
//        return output.concat(" You are running spring shell cli-demo.");
//    }
package com.pervasivecode.utils.concurrent.cucumber;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import java.io.PrintWriter;
import java.io.StringWriter;
import com.pervasivecode.utils.concurrent.example.ExampleApplication;
import com.pervasivecode.utils.concurrent.example.TriathlonTimingExample;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

public class StepDefinitions {
  private String commandOutput = "";
  private ExampleApplication codeExample = null;

  @Given("^I am running the Triathlon Timing Example$")
  public void iAmRunningTheTriathlonTimingExample() {
    this.codeExample = new TriathlonTimingExample();
    this.commandOutput = "";
  }

  @When("^I run the program$")
  public void iRunTheProgram() throws Exception {
    checkNotNull(this.codeExample, "did you forget an 'I am running the' example step?");
    StringWriter sw = new StringWriter();
    this.codeExample.runExample(new PrintWriter(sw, true));
    commandOutput = commandOutput.concat(sw.toString());
  }

  @Then("^I should see the output$")
  public void iShouldSeeTheOutput(String expected) {
    assertThat(this.commandOutput).isEqualTo(expected);
  }
}

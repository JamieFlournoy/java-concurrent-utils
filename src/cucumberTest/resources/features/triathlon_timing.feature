Feature: Triathlon Timing Example

  Scenario: Triathlon Timing Example
  	Given I am running the Triathlon Timing Example
  	When I run the program
 	Then I should see the output
    """
    Event times for running: 3s, 4s, 5s
    Timing summary for running: 12s (4s avg, 3 cycles)
    Timing summary for swimming: 0s (0 cycles)
    Timing summary for bicycling: 0s (0 cycles)

    Event times for swimming: 7s, 8s, 9s
    Timing summary for running: 12s (4s avg, 3 cycles)
    Timing summary for swimming: 24s (8s avg, 3 cycles)
    Timing summary for bicycling: 0s (0 cycles)

    Event times for bicycling: 2s, 3s, 4s
    Timing summary for running: 12s (4s avg, 3 cycles)
    Timing summary for swimming: 24s (8s avg, 3 cycles)
    Timing summary for bicycling: 9s (3s avg, 3 cycles)

    """

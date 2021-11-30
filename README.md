# Links
## Firebase database
https://console.firebase.google.com/u/0/project/mixedupgame/database/mixedupgame-default-rtdb/data

## adobe XD walkthrough
https://xd.adobe.com/view/d865483a-67ff-4a2c-bced-c4d787b48586-8d36/screen/7c679a4d-125c-429d-bfe9-65bd4ffe7b58/

## help
android studio wig out help - https://stackoverflow.com/questions/20226912/android-studio-inline-compiler-showing-red-errors-but-compilation-with-gradle-w

user help - https://stackoverflow.com/questions/38114358/firebase-setdisplayname-of-user-while-creating-user-android

Firebase db child even listener help - https://www.titanwolf.org/Network/q/26edfd1b-c54c-4431-b147-acece2238472/y

Toast from viewmodel help - https://code.luasoftware.com/tutorials/android/android-use-livedata-to-show-toast-message-from-viewmodel/

Off. Doc. Firebase account help - https://firebase.google.com/docs/auth/android/password-auth
Off. Doc. Firebase account error help - https://firebase.google.com/docs/reference/js/v8/firebase.auth.Auth#signinwithemailandpassword

help with recycler view - https://stackoverflow.com/questions/40584424/simple-android-recyclerview-example
                        - https://www.journaldev.com/13792/android-gridlayoutmanager-example

making own class comparable to sort in array - https://stackoverflow.com/questions/13051568/making-your-own-class-comparable (NOT NEEDED ANYMORE)

making spinner - https://stackoverflow.com/questions/13377361/how-to-create-a-drop-down-list
               - slightly modified Md. Kamruzzamans answer for my nameing conventions, used his classes and ideas! Thanks!
help with spinner - https://android--examples.blogspot.com/2016/10/android-spinner-on-item-selected.html

iterating through views help - https://stackoverflow.com/questions/4809834/how-to-iterate-through-a-views-elements

collections help - https://stackoverflow.com/questions/22989806/find-the-most-common-string-in-arraylist

scroll view fix - https://stackoverflow.com/questions/38663428/android-scrollview-gets-cut-off-at-the-bottom (answer by Ross)

disabling nightview = https://stackoverflow.com/questions/57175226/how-to-disable-night-mode-in-my-application-even-if-night-mode-is-enable-in-andr

button help - https://stackoverflow.com/questions/15615823/setenabled-vs-setclickable-what-is-the-difference

Maybe randomize then sentence based on the person before them instead of after since if is after??

## todo
send players if statements to db - DONE
set up observer on database to see changes and to add new user to list of users - DONE
set up onlistchanged callback in collecting ifs fragment to show who has submitted there answer - DONE
set up to move to write then fragment once everyones ifs are in - DONE
in setting up write then frag make sure players dont get their own if sentance. - DONE
set up onlistchanged callback in collecting thens fragment to show who has submitted there answer - DONE
set up to move to readSentance frag once everyones thens are in. - DONE
mix up everyones Then sentence so they dont have their own then - DONE
  add dropdown to read sentence frag if they are account play - DONE

  after they hit next from read sentance have them vote on best sentance (if all users are account play). - DONE
  compare with what is on the leaderboards and if the new sentence wins push it to the leaderboards and remove the old one - DONE
  create unlockables class that will be part of user players stored in FB - DONE
  End frag can return to first frag or play again. - Done
  create leaderboards fragment and buttons to link to current frags - DONE
  write string mutators to change text based on different voices - Partly DONE
  create player profile that fills with user info, and games played -
  create ways to unlock each unlockable voice and implement - DO NEXT
  create push notifications - DO NEXT


## First User test
    5 players
    found a bunch of issues I didnt think of, created a list of 22 things to fix:
### DONE
    - assure that a phone with dark mode on doesnt change text color in app
    - if free play has written name in field have it auto populate field with users name
    - assure text in if and then sentence fields 
    - fix highlighting in vote frag
    - lockdown gameroom if game is in progress so no one else can join
    - provide more back buttons on select fragments
    - android version compatibility is 25 and greater
    - strip whitespace on if and then sentences, remove punctuation so it can be standardized
    - assure first letter of if sentence is capitol
    - make again button greyed out on ending frag for those who arent host, then enable when host plays again
    - make sure all votes are cast before host clicks again or home on end frag.
    - fix issue where voting items in database arent reset when players play again
    - assure guests and users with same name in gameroom doesnt cause issues with db
    - fixed leaderboards not loading in every time users try to go to them
    - fixed scrollview issues in leaderboard and vote frags
    - fix small UI issues with edit texts and some textviews 
    - add period to leaderboards sentences    
    - line up sentences in vote frag
### NOT DONE
    - strip whitespace on account frag
    - alert users if they have a sentence in the leaderboards
    - check constraint layout on frags to assure UI is good
    - assure pushing and pulling from database is successful if connection fails

## second user test
    3 players
    - a group played game smoothly multiple times no issue!
    - need to fix when players from group split and go to new group, 
      issues with a user that wasnt a host goes to new group and isnt a host there as well
    

## questions
    - is there a good way to reinitialize a db reference to have no listeners at the end of a match?
    - is the db listeners on the db reference object?
    - if a push or a pull to the db fails is there a way to retry until it is successful?

## ideas
    - remove ambiguous letters from game code generation
    - make the username the account players email address and what is currently the user name the display name
    - app design - don't allow the user to make a mistake if possible (vote frag selecting more than one sentence)
    - leaderboards (currently isnt showing which player is the best player, food for thought)

@Component({
  selector: 'my-app', 
  template: require('test008.html')
})
export class AppComponent {
  title = 'Tour of Heroes';
  heroes = [];
  selectedHero = { firstName: "eee" }
}

import { Component, OnInit, inject } from '@angular/core';
import { Subscription } from 'rxjs';
import { ResourceService } from '../../service/resource.service';
import { Resource } from '../../model/Resource';

@Component({
  selector: 'app-resources',
  standalone: true,
  imports: [],
  templateUrl: './resources.component.html',
  styleUrl: './resources.component.css'
})
export class ResourcesComponent implements OnInit {

  private resourceService = inject(ResourceService);
  private subscription!: Subscription;
  resources : Resource[] = [];

  ngOnInit() {
    this.subscription = this.resourceService.getResources().subscribe((res: any[]) => {
      console.log(res);
      this.resources = res;
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

}

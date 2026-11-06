import { createBrowserRouter } from 'react-router-dom'
import { Layout } from '@/components/layout'
import { HomePage } from '@/pages/home'
import { AboutPage } from '@/pages/about'
import { NotFoundPage } from '@/pages/not-found'

export const router = createBrowserRouter([{
  element: <Layout />,
  children: [
    { path: '/', element: <HomePage /> },
    { path: '/sobre', element: <AboutPage /> },
    { path: '*', element: <NotFoundPage /> },
  ],
}])
